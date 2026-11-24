package com.picacg.coui.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 接口调用失败。message 直接展示在界面上，所以写成能看懂的中文。 */
class ApiException(message: String) : Exception(message)

/** 「测试连接」的结果。 */
data class ProbeResult(
    val ok: Boolean,
    val message: String,
    val url: String,
    val preview: String,
)

/** 登录结果。[token] 非空表示成功，调用方负责把它存进配置。 */
data class LoginResult(
    val ok: Boolean,
    val message: String,
    val token: String,
    val url: String,
    val preview: String,
)

/**
 * 通用 HTTP 数据源。
 *
 * 只依赖 [ApiConfig] 里的地址与路径，不含任何业务逻辑。
 * JSON 解析是「自适应」的：会自己找列表所在的位置，也会猜常见字段名，
 * 所以大部分 REST 接口不用改代码就能直接跑起来。字段名对不上时，
 * 在设置页点「测试连接」能看到原始响应，据此调整路径或字段即可。
 */
class HttpRepository(private val cfg: ApiConfig) : ContentRepository {

    /**
     * 内置模式下「分类名 → 分类 id」的映射。
     * 哔咔的分类筛选参数要的是 id（`?c=xxx`），而界面上显示的是中文名，
     * 所以拉分类列表时顺手记下来，切分类时直接拿来用。
     */
    private val categoryIds = mutableMapOf<String, String>()

    /**
     * 封面字段的候选名。不同后端的叫法差别很大（picacg 用 thumb，
     * 常见 CMS 用 cover / coverImage / poster），一次全试一遍比让用户手填字段名省事。
     */
    private val COVER_KEYS = arrayOf(
        "cover", "coverUrl", "cover_url", "coverImage", "cover_image",
        "image", "imageUrl", "image_url", "img", "pic", "picture",
        "thumb", "thumbnail", "thumbUrl", "poster", "posterUrl",
        "banner", "listCover", "avatar",
    )


    // ---------------------------------------------------------------- 对外接口

    override suspend fun home(page: Int): FeedPage = withContext(Dispatchers.IO) {
        val body = request(cfg.homePath, mapOf("page" to page.toString()))
        val (items, blocked) = applyBlock(parseList(body))
        FeedPage(items = items, hasMore = items.isNotEmpty(), blocked = blocked)
    }

    /**
     * 带分类的首页。
     *
     * 只有在路径里显式写了 `{category}` 时才会真的把分类发给后端，
     * 否则退回普通的 [home]——因为乱加一个后端不认识的 query 参数，
     * 有些服务端会直接 400。
     */
    override suspend fun homeByCategory(page: Int, category: String): FeedPage =
        withContext(Dispatchers.IO) {
            val noFilter = category.isBlank() || category == "全部"
            if (noFilter) return@withContext home(page)

            // 内置哔咔接口按分类 id 过滤，先把名字换成 id
            if (cfg.builtin) {
                val id = categoryIds[category]
                val path = if (id != null) "/comics?c=$id&page={page}" else cfg.homePath
                val body = request(path, mapOf("page" to page.toString()))
                val (items, blocked) = applyBlock(parseList(body))
                return@withContext FeedPage(
                    items = items,
                    hasMore = items.isNotEmpty(),
                    blocked = blocked,
                )
            }

            if (!cfg.homePath.contains("{category}")) return@withContext home(page)

            val body = request(
                cfg.homePath,
                mapOf("page" to page.toString(), "category" to category),
            )
            val (items, blocked) = applyBlock(parseList(body))
            FeedPage(items = items, hasMore = items.isNotEmpty(), blocked = blocked)
        }

    override suspend fun categories(): List<String> = withContext(Dispatchers.IO) {
        val body = request(cfg.categoriesPath, emptyMap())
        val root = parseJson(body) ?: return@withContext emptyList()
        val arr = findArray(root)
        if (arr != null) {
            (0 until arr.length()).mapNotNull { i ->
                when (val e = arr.opt(i)) {
                    is String -> e.takeIf { it.isNotBlank() }
                    is JSONObject -> {
                        val name = str(e, "name", "title", "label", "value")
                        // 记下 id，后面按分类筛选时要用（哔咔是 ?c=<id>）
                        val id = str(e, "_id", "id", "key", "categoryId")
                        if (name != null && id != null) categoryIds[name] = id
                        name
                    }
                    else -> e?.toString()?.takeIf { it.isNotBlank() }
                }
            }.distinct()
        } else {
            // 有的接口返回 {"分类A": [...], "分类B": [...]}，取键名当分类
            (root as? JSONObject)?.keys()?.asSequence()?.toList().orEmpty()
        }
    }

    override suspend fun ranking(kind: String): List<CardItem> = withContext(Dispatchers.IO) {
        // 内置哔咔接口的榜期参数是 H24 / D7 / D30，界面上用的是 day / week / month
        val mapped = if (cfg.builtin) when (kind.lowercase()) {
            "day" -> "H24"
            "week" -> "D7"
            "month" -> "D30"
            else -> kind
        } else kind
        val body = request(cfg.rankingPath, mapOf("kind" to mapped))
        applyBlock(parseList(body)).first.mapIndexed { i, c ->
            if (c.badge.isBlank()) c.copy(badge = (i + 1).toString()) else c
        }
    }

    override suspend fun search(keyword: String): List<CardItem> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        val body = request(cfg.searchPath, mapOf("keyword" to keyword))
        applyBlock(parseList(body)).first
    }

    override suspend fun detail(id: String): DetailItem = withContext(Dispatchers.IO) {
        val body = request(cfg.detailPath, mapOf("id" to id))
        val root = parseJson(body) ?: throw ApiException("响应不是合法 JSON")
        val o = unwrapDetail(root)
            ?: throw ApiException("详情接口返回的不是对象，请在设置里检查路径")

        val chaptersArr = findArrayByKeys(o, listOf("chapters", "eps", "episodes", "chapterList", "chapter_list"))
        var chapters = if (chaptersArr != null) {
            (0 until chaptersArr.length()).mapNotNull { i ->
                val c = chaptersArr.optJSONObject(i) ?: return@mapNotNull null
                Chapter(
                    id = str(c, "id", "_id", "chapterId", "cid") ?: "c$i",
                    title = str(c, "title", "name", "chapterName") ?: "第 ${i + 1} 话",
                    pages = num(c, "pages", "pageCount", "page_count", "total") ?: 0,
                )
            }
        } else {
            emptyList()
        }

        // 哔咔的详情接口本身不带章节，章节在 /comics/{id}/eps 里，单独补一次
        if (chapters.isEmpty() && cfg.builtin) {
            chapters = runCatching { fetchEpisodes(id) }.getOrDefault(emptyList())
        }

        DetailItem(
            id = str(o, "id", "_id", "comicId", "uuid") ?: id,
            title = str(o, "title", "name", "comicName", "bookName") ?: "未命名",
            author = str(o, "author", "artist", "authorName", "creator") ?: "",
            description = str(o, "description", "desc", "summary", "intro", "content") ?: "",
            coverUrl = imageOf(o, *COVER_KEYS),
            tags = tagsOf(o),
            chapters = chapters,
            likes = num(o, "likes", "like", "likeCount", "favoriteCount") ?: 0,
            views = num(o, "views", "view", "viewCount", "readCount", "popularity") ?: 0,
        ).also { d ->
            // 详情也走屏蔽规则，避免从排行、搜索这类入口直接点进来绕过列表过滤
            if (cfg.block.active) {
                val blocked = cfg.block.blocks(
                    d.id,
                    mapOf(
                        "title" to d.title,
                        "author" to d.author,
                        "subtitle" to d.description,
                        "tags" to d.tags.joinToString(" / "),
                    ),
                )
                if (blocked) throw ApiException("该内容已被你的屏蔽规则拦截：" + d.title.take(20))
            }
        }
    }

    override suspend fun announcements(): List<Announcement> = withContext(Dispatchers.IO) {
        val body = request(cfg.announcementsPath, emptyMap())
        val root = parseJson(body) ?: return@withContext emptyList()
        val arr = findArray(root) ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = str(o, "title", "name", "subject") ?: return@mapNotNull null
            Announcement(
                id = str(o, "id", "_id", "uuid") ?: "a$i",
                title = title,
                content = str(o, "content", "message", "body", "text", "description") ?: "",
                date = str(o, "date", "createdAt", "created_at", "time", "updatedAt")?.take(10) ?: "",
            )
        }
    }

    /** 拉某一本漫画的章节列表（哔咔：/comics/{id}/eps）。失败不影响详情主体。 */
    private fun fetchEpisodes(id: String): List<Chapter> {
        val body = request("/comics/{id}/eps", mapOf("id" to id))
        val arr = findArray(parseJson(body)) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val c = arr.optJSONObject(i) ?: return@mapNotNull null
            Chapter(
                id = str(c, "_id", "id", "chapterId", "cid") ?: "c$i",
                title = str(c, "title", "name", "chapterName") ?: "第 ${i + 1} 话",
                pages = num(c, "pages", "pageCount", "page_count", "total") ?: 0,
            )
        }
    }

    // ---------------------------------------------------------------- 登录

    /**
     * 用账号密码登录，换回一个 token。
     *
     * 请求体同时带 email 和 username 两个字段（值相同），兼容不同后端对账号字段的命名，
     * 多数服务端会忽略自己不认识的字段。返回体里会自动找 token / access_token / jwt 等。
     */
    suspend fun login(account: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val url = cfg.absoluteUrl(cfg.loginPath)
        if (account.isBlank() || password.isBlank()) {
            return@withContext LoginResult(false, "账号和密码都要填", "", url, "")
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext LoginResult(
                false, "接口地址要以 http:// 或 https:// 开头，当前是：$url", "", url, "",
            )
        }
        try {
            val payload = JSONObject().apply {
                put("email", account)
                // 哔咔只认 email，多塞字段会被判成参数错误；自定义接口才带上 username 兼容
                if (!cfg.builtin) put("username", account)
                put("password", password)
            }.toString()
            val body = postJson(cfg.loginPath, payload)
            val root = parseJson(body)
            val token = extractToken(root)
            if (token.isNullOrBlank()) {
                // 服务端通常会在这里给出「密码错误」「账号不存在」之类的原因，优先展示它
                val serverMsg = (root as? JSONObject)
                    ?.let { str(it, "message", "msg", "error", "errorMessage", "detail") }
                LoginResult(
                    ok = false,
                    message = if (serverMsg != null) "登录失败：$serverMsg"
                    else "连上了，但返回里没找到 token。看下面的原始返回，确认登录路径和字段名对不对。",
                    token = "",
                    url = url,
                    preview = body.take(1500),
                )
            } else {
                LoginResult(
                    ok = true,
                    message = "登录成功，令牌已保存，各页面会自动带上",
                    token = token,
                    url = url,
                    preview = body.take(800),
                )
            }
        } catch (e: Exception) {
            LoginResult(false, e.message ?: "登录请求失败", "", url, "")
        }
    }

    // ---------------------------------------------------------------- 测试连接

    /**
     * 用当前配置请求一次首页接口，把结果反馈给设置页。
     * 失败时也会尽量把响应内容带出来，方便对照字段。
     */
    suspend fun probe(): ProbeResult = withContext(Dispatchers.IO) {
        val url = cfg.absoluteUrl(cfg.homePath.replace("{page}", "1"))
        try {
            val body = request(cfg.homePath, mapOf("page" to "1"))
            val items = parseList(body)
            ProbeResult(
                ok = true,
                message = if (items.isEmpty()) "连上了，但没解析出条目，检查路径与字段名"
                else "连上了，解析到 ${items.size} 条：${items.take(3).joinToString("、") { it.title }}",
                url = url,
                preview = body.take(1500),
            )
        } catch (e: Exception) {
            ProbeResult(false, e.message ?: e.toString(), url, "")
        }
    }

    // ---------------------------------------------------------------- 屏蔽

    /**
     * 按当前屏蔽规则过滤一批卡片，返回剩下的和被拦掉的条数。
     *
     * 规则在这里统一生效，主页 / 分类 / 排行 / 搜索都不用各自处理。
     * 规则关闭时直接短路返回，不产生任何额外开销。
     */
    private fun applyBlock(items: List<CardItem>): Pair<List<CardItem>, Int> {
        val rules = cfg.block
        if (!rules.active) return items to 0
        val kept = items.filterNot { c ->
            rules.blocks(
                c.id,
                mapOf(
                    "title" to c.title,
                    "subtitle" to c.subtitle,
                    // 列表里没有独立作者字段，副标题通常带作者名，两者都让 author: 规则可命中
                    "author" to c.subtitle,
                    "tags" to c.tags.joinToString(" / "),
                ),
            )
        }
        return kept to (items.size - kept.size)
    }

    // ---------------------------------------------------------------- 网络

    private fun request(path: String, vars: Map<String, String>): String {
        val url = cfg.absoluteUrl(fill(path, vars))
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw ApiException("接口地址要以 http:// 或 https:// 开头，当前是：$url")
        }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            applyHeaders(this, "GET", url)
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw ApiException("HTTP $code：${text.take(180).ifBlank { "无响应内容" }}")
            }
            if (text.isBlank()) throw ApiException("接口返回了空内容")
            return text
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("请求失败：${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 请求头。
     *
     * 内置哔咔接口要求一套签名：把「路径 + 时间戳 + 随机串 + 方法 + api-key」
     * 用 api-key 做 HMAC-SHA256，结果放进 signature 头。这是开源客户端公开的算法。
     * 自定义接口走普通 JSON 头，不签名。
     */
    private fun applyHeaders(conn: HttpURLConnection, method: String, url: String) {
        if (cfg.builtin) {
            val path = runCatching { URL(url).path.ifBlank { "/" } }.getOrDefault("/")
            val ts = (System.currentTimeMillis() / 1000).toString()
            val nonce = UUID.randomUUID().toString().replace("-", "")
            val sig = hmacSha256(path + ts + nonce + method + ApiConfig.PICACG_API_KEY)
            conn.setRequestProperty("api-key", ApiConfig.PICACG_API_KEY)
            conn.setRequestProperty("nonce", nonce)
            conn.setRequestProperty("timestamp", ts)
            conn.setRequestProperty("signature", sig)
            conn.setRequestProperty("app-channel", "11")
            conn.setRequestProperty("app-version", "2.2.1.3.3.4")
            conn.setRequestProperty("app-build-version", "44")
            conn.setRequestProperty(
                "app-uuid",
                DeviceId.value.ifBlank { "00000000-0000-0000-0000-000000000000" },
            )
            conn.setRequestProperty("app-platform", "android")
            conn.setRequestProperty("app-mode", "3")
            conn.setRequestProperty("app-source", "android-app")
            conn.setRequestProperty("accept", "application/vnd.picacomic.com.v1+json")
            conn.setRequestProperty("user-agent", "okhttp/3.12.0")
        } else {
            conn.setRequestProperty("Accept", "application/json, */*")
            conn.setRequestProperty("User-Agent", "COUI-Demo/1.0 (Android)")
        }
        applyAuth(conn)
    }

    /** 用 api-key 做 HMAC-SHA256，输出小写十六进制。 */
    private fun hmacSha256(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(ApiConfig.PICACG_API_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** GET 与 POST 共用的鉴权头。令牌原样发送还是加 Bearer 前缀，由设置决定。 */
    private fun applyAuth(conn: HttpURLConnection) {
        val t = cfg.token.trim()
        if (t.isEmpty()) return
        val authValue = when {
            cfg.tokenRaw || cfg.builtin -> t
            t.contains(' ') -> t
            else -> "Bearer $t"
        }
        conn.setRequestProperty("Authorization", authValue)
        conn.setRequestProperty("token", t)
    }

    /** 发一个 JSON POST 请求，返回响应正文。 */
    private fun postJson(path: String, payload: String): String {
        val url = cfg.absoluteUrl(path)
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw ApiException("接口地址要以 http:// 或 https:// 开头，当前是：$url")
        }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            applyHeaders(this, "POST", url)
        }
        try {
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw ApiException("HTTP $code：${text.take(180).ifBlank { "无响应内容" }}")
            }
            if (text.isBlank()) throw ApiException("登录接口返回了空内容")
            return text
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("请求失败：${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 在响应里找令牌。按常见 key 优先找，找不到再递归最深 4 层，
     * 所以 {"data": {"token": "..."}} 这种包装也能取到。
     */
    private fun extractToken(node: Any?, depth: Int = 0): String? {
        if (node == null || depth > 4) return null
        if (node is JSONObject) {
            for (k in listOf("token", "access_token", "accessToken", "authToken", "api_token", "jwt", "authorization")) {
                val v = node.opt(k)
                if (v is String && v.isNotBlank()) return v
            }
            for (k in node.keys()) {
                extractToken(node.opt(k), depth + 1)?.let { return it }
            }
        }
        if (node is JSONArray) {
            for (i in 0 until node.length()) {
                extractToken(node.opt(i), depth + 1)?.let { return it }
            }
        }
        return null
    }

    /** 把 {page} / {id} 这类占位符替换成实际值。 */
    private fun fill(path: String, vars: Map<String, String>): String {
        var p = path
        for ((k, v) in vars) {
            p = p.replace("{$k}", URLEncoder.encode(v, "UTF-8"))
        }
        return p
    }

    // ---------------------------------------------------------------- JSON 解析

    private fun parseJson(body: String): Any? = try {
        val t = body.trim()
        when {
            t.startsWith("[") -> JSONArray(t)
            t.startsWith("{") -> JSONObject(t)
            else -> null
        }
    } catch (e: Exception) {
        throw ApiException("JSON 解析失败：${e.message}")
    }

    private fun parseList(body: String): List<CardItem> {
        val root = parseJson(body) ?: throw ApiException("响应不是合法 JSON")
        val arr = findArray(root) ?: throw ApiException("响应里没找到列表，请看「测试连接」里的原始内容")
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { objToCard(it, i) }
        }
    }

    /**
     * 在任意 JSON 结构里找到「元素为对象的数组」。
     * 先按常见 key 找，找不到就递归最深 4 层。绝大多数接口都能命中。
     */
    private fun findArray(node: Any?, depth: Int = 0): JSONArray? {
        if (node == null || depth > 4) return null
        if (node is JSONArray) {
            if (node.length() == 0) return node
            if (node.opt(0) is JSONObject) return node
            return null
        }
        if (node is JSONObject) {
            val preferred = listOf(
                "list", "docs", "items", "comics", "data", "result", "results",
                "records", "rows", "content", "books", "posts", "articles",
            )
            for (k in preferred) {
                if (!node.has(k) || node.isNull(k)) continue
                val v = node.opt(k)
                if (v is JSONArray && (v.length() == 0 || v.opt(0) is JSONObject)) return v
            }
            for (k in node.keys()) {
                if (k in preferred) continue
                val v = node.opt(k)
                if (v is JSONArray && v.length() > 0 && v.opt(0) is JSONObject) return v
            }
            for (k in node.keys()) {
                val v = node.opt(k)
                if (v is JSONObject) findArray(v, depth + 1)?.let { return it }
            }
            for (k in node.keys()) {
                val v = node.opt(k)
                if (v is JSONArray) findArray(v, depth + 1)?.let { return it }
            }
        }
        return null
    }

    /**
     * 详情响应的包装层数不固定，常见三种：
     *   {id,title,...}            —— 直接给
     *   {data:{id,title,...}}     —— 一层
     *   {data:{comic:{...}}}      —— 两层（picaapi / 多数博客型接口都是这种）
     * 这里逐层往里剥，一旦看到「像详情对象」的特征键就停。
     */
    private fun unwrapDetail(root: Any?): JSONObject? {
        var o = root as? JSONObject ?: return null
        repeat(3) {
            if (o.has("title") || o.has("name") || o.has("comicName") ||
                o.has("bookName") || o.has("chapters") || o.has("epsCount")
            ) {
                return o
            }
            val next: JSONObject? = o.optJSONObject("data")
                ?: listOf("comic", "book", "detail", "info", "item")
                    .firstNotNullOfOrNull { k -> o.optJSONObject(k) }
            if (next == null || next === o) return o
            o = next
        }
        return o
    }

    /** 按指定 key 找数组，找不到再递归。 */
    private fun findArrayByKeys(o: JSONObject, keys: List<String>): JSONArray? {
        for (k in keys) {
            val v = o.opt(k)
            if (v is JSONArray) return v
            if (v is JSONObject) {
                v.optJSONArray("list")?.let { return it }
                v.optJSONArray("docs")?.let { return it }
            }
        }
        val data = o.optJSONObject("data")
        if (data != null && data !== o) return findArrayByKeys(data, keys)
        return null
    }

    private fun objToCard(o: JSONObject, index: Int): CardItem {
        val id = str(o, "id", "_id", "comicId", "comic_id", "uuid", "key", "sid", "pathWord")
            ?: "item-$index"
        val title = str(o, "title", "name", "comicName", "comic_name", "bookName", "nickname")
            ?: "未命名"
        val subtitle = str(
            o, "subtitle", "description", "desc", "summary", "intro",
            "author", "category", "type", "tag",
        ).orEmpty()
        val cover = imageOf(o, *COVER_KEYS)
        val badge = str(o, "badge", "rank", "order", "sort").orEmpty()
        return CardItem(
            id = id,
            title = title,
            subtitle = subtitle,
            coverUrl = cover,
            badge = badge,
            tags = tagsOf(o),
        )
    }

    /** 取值：字符串直接返回，数字/布尔转字符串，对象取 name/title，数组拼成 "A / B"。 */
    private fun str(o: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            if (!o.has(k) || o.isNull(k)) continue
            when (val v = o.opt(k)) {
                is String -> if (v.isNotBlank()) return v
                is Number, is Boolean -> return v.toString()
                is JSONObject -> str(v, "name", "title", "value", "text")?.let { return it }
                is JSONArray -> {
                    val parts = (0 until v.length()).mapNotNull { i ->
                        when (val e = v.opt(i)) {
                            is String -> e.takeIf { s -> s.isNotBlank() }
                            is JSONObject -> str(e, "name", "title", "value")
                            else -> e?.toString()?.takeIf { s -> s.isNotBlank() }
                        }
                    }
                    if (parts.isNotEmpty()) return parts.joinToString(" / ")
                }
            }
        }
        return null
    }

    private fun num(o: JSONObject, vararg keys: String): Int? {
        for (k in keys) {
            if (!o.has(k) || o.isNull(k)) continue
            when (val v = o.opt(k)) {
                is Number -> return v.toInt()
                is String -> v.toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    /**
     * 封面地址。支持三种常见形态：
     * 1. 直接给完整 URL / 相对路径
     * 2. 给一个对象，里面有 url / path
     * 3. 给 fileServer + path 两段，需要拼起来
     */
    private fun imageOf(o: JSONObject, vararg keys: String): String {
        for (k in keys) {
            if (!o.has(k) || o.isNull(k)) continue
            when (val v = o.opt(k)) {
                is String -> if (v.isNotBlank()) return absolute(v)
                is JSONObject -> {
                    val direct = str(v, "url", "src", "originalName", "origin", "path")
                    val server = str(v, "fileServer", "server", "host")
                    val path = str(v, "path")
                    if (server != null && path != null) {
                        return absolute(server.trimEnd('/') + "/" + path.trimStart('/'))
                    }
                    if (direct != null) return absolute(direct)
                }
            }
        }
        return ""
    }

    private fun absolute(u: String): String =
        if (u.startsWith("http://") || u.startsWith("https://")) u
        else cfg.absoluteUrl(u)

    private fun tagsOf(o: JSONObject): List<String> {
        val keys = listOf("tags", "tag", "categories", "category", "labels", "genres")
        for (k in keys) {
            if (!o.has(k) || o.isNull(k)) continue
            val list: List<String> = when (val v = o.opt(k)) {
                is JSONArray -> (0 until v.length()).mapNotNull { i ->
                    when (val e = v.opt(i)) {
                        is String -> e
                        is JSONObject -> str(e, "name", "title", "value")
                        else -> e?.toString()
                    }
                }
                is String -> v.split(",", "，", "|", "/").map { it.trim() }
                is JSONObject -> listOfNotNull(str(v, "name", "title"))
                else -> emptyList()
            }
            // 多留几枚：列表只显示前三枚，详情页和以后要扩的展示位直接复用
            val clean = list.filter { it.isNotBlank() && it.length <= 12 }.distinct().take(6)
            if (clean.isNotEmpty()) return clean
        }
        return emptyList()
    }
}
