package com.picacg.coui.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

/**
 * 接口配置。
 *
 * 默认走「内置哔咔接口」：地址、路径、请求签名都由 App 自己带好，
 * 用户只需要登录，不用再手填一堆路径。
 * 需要接自己的后端时，在设置里关掉内置开关，填地址即可。
 */
data class ApiConfig(
    /** 是否使用内置哔咔接口（picaapi）。默认开启。 */
    val builtin: Boolean = true,
    /** 是否启用自定义接口。仅在内置关闭时有效。 */
    val useCustom: Boolean = true,
    /** 接口根地址。内置模式下由 [PICACG_BASE] 提供，这里留空即可。 */
    val baseUrl: String = "",
    /** 访问令牌，登录成功后自动填入。 */
    val token: String = "",
    /** Authorization 头是否原样发送 token（不加 Bearer 前缀）。 */
    val tokenRaw: Boolean = true,
    /** 登录接口路径。 */
    val loginPath: String = "/auth/sign-in",
    /** 上次登录用的账号，仅用于回填输入框。 */
    val account: String = "",
    /** 首页信息流。{page} 会替换为页码。 */
    val homePath: String = "/comics?page={page}",
    /** 分类列表。 */
    val categoriesPath: String = "/categories",
    /** 排行榜。{kind} 会替换为 day / week / month（内置模式会自动映射成 H24 / D7 / D30）。 */
    val rankingPath: String = "/comics/leaderboard?tt={kind}",
    /** 搜索。{keyword} 会替换为关键词。 */
    val searchPath: String = "/comics/search?keyword={keyword}",
    /** 详情。{id} 会替换为条目 id。 */
    val detailPath: String = "/comics/{id}",
    /** 公告列表。 */
    val announcementsPath: String = "/announcements",
    /** 屏蔽规则，见 [BlockRules]。 */
    val block: BlockRules = BlockRules(),
) {
    /** 内置接口永远可用；自定义接口要有根地址。 */
    val enabled: Boolean get() = builtin || (useCustom && baseUrl.isNotBlank())

    /** 当前实际使用的根地址。 */
    val effectiveBase: String
        get() = if (builtin) PICACG_BASE else baseUrl.trim().trimEnd('/')

    /** 拼出完整 URL，相对路径会自动接在根地址后面。 */
    fun absoluteUrl(path: String): String {
        val p = path.trim()
        if (p.startsWith("http://") || p.startsWith("https://")) return p
        val base = effectiveBase
        if (p.isEmpty()) return base
        return base + "/" + p.trimStart('/')
    }

    companion object {
        /** 哔咔官方接口，和 picacg 各开源客户端用的是同一个。 */
        const val PICACG_BASE = "https://picaapi.picacomic.com"

        /** 开源客户端里公开的 api-key，用于请求签名，不是账号凭据。 */
        const val PICACG_API_KEY = "C69BAF41DA5ABD1FFEDC6D2F1E56A92A"

        val PICACG = ApiConfig(builtin = true)

        val DEFAULT = PICACG
    }
}

/**
 * 设备标识。哔咔接口要求每个客户端带一个稳定的 app-uuid，
 * 首次启动生成一次后落盘，之后一直复用。
 */
object DeviceId {
    var value: String = ""
        private set

    fun init(sp: SharedPreferences) {
        val saved = sp.getString(KEY, null)
        if (!saved.isNullOrBlank()) {
            value = saved
            return
        }
        val id = UUID.randomUUID().toString()
        value = id
        sp.edit().putString(KEY, id).apply()
    }

    private const val KEY = "device_uuid"
}

/**
 * 全局配置仓库。用 SharedPreferences 持久化，同时暴露成 Compose 状态。
 * 需要在 Activity 启动时调用一次 [init]。
 */
object ApiConfigStore {

    private const val PREF_NAME = "coui_api_config"

    private var prefs: SharedPreferences? = null

    var config by mutableStateOf(ApiConfig())
        private set

    var version by mutableIntStateOf(0)
        private set

    var darkMode by mutableStateOf(false)
        private set

    /**
     * 兼容模式：关闭背景模糊、COUI 顶栏/底栏等自定义绘制，
     * 用于某些 ROM 上一进就闪退的兜底。启动检测到上次崩溃会自动打开。
     */
    var compatMode by mutableStateOf(false)
        private set

    fun applyDark(value: Boolean) {
        darkMode = value
        prefs?.edit()?.putBoolean(KEY_DARK, value)?.apply()
    }

    fun applyCompat(value: Boolean) {
        compatMode = value
        prefs?.edit()?.putBoolean(KEY_COMPAT, value)?.apply()
    }

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs = sp
        DeviceId.init(sp)
        darkMode = sp.getBoolean(KEY_DARK, false)
        compatMode = sp.getBoolean(KEY_COMPAT, false)

        val builtin = sp.getBoolean(KEY_BUILTIN, true)
        val d = ApiConfig.PICACG
        config = ApiConfig(
            builtin = builtin,
            useCustom = sp.getBoolean(KEY_USE_CUSTOM, true),
            baseUrl = if (builtin) "" else (sp.getString(KEY_BASE, "") ?: ""),
            token = sp.getString(KEY_TOKEN, "") ?: "",
            tokenRaw = sp.getBoolean(KEY_TOKEN_RAW, true),
            loginPath = pathOf(sp, KEY_LOGIN, d.loginPath, builtin),
            account = sp.getString(KEY_ACCOUNT, "") ?: "",
            homePath = pathOf(sp, KEY_HOME, d.homePath, builtin),
            categoriesPath = pathOf(sp, KEY_CATS, d.categoriesPath, builtin),
            rankingPath = pathOf(sp, KEY_RANK, d.rankingPath, builtin),
            searchPath = pathOf(sp, KEY_SEARCH, d.searchPath, builtin),
            detailPath = pathOf(sp, KEY_DETAIL, d.detailPath, builtin),
            announcementsPath = pathOf(sp, KEY_NOTICE, d.announcementsPath, builtin),
            block = BlockRules(
                enabled = sp.getBoolean(KEY_BLOCK_ON, false),
                raw = sp.getString(KEY_BLOCK_RAW, BlockRules.SAMPLE) ?: BlockRules.SAMPLE,
            ),
        )
    }

    /** 内置模式用代码里的路径；自定义模式读本地保存的值。 */
    private fun pathOf(sp: SharedPreferences, key: String, fallback: String, builtin: Boolean): String =
        if (builtin) fallback else (sp.getString(key, fallback) ?: fallback)

    fun save(next: ApiConfig) {
        config = next
        val e = prefs?.edit()
            ?.putBoolean(KEY_BUILTIN, next.builtin)
            ?.putBoolean(KEY_USE_CUSTOM, next.useCustom)
            ?.putString(KEY_BASE, if (next.builtin) "" else next.baseUrl.trim())
            ?.putString(KEY_TOKEN, next.token.trim())
            ?.putBoolean(KEY_TOKEN_RAW, next.tokenRaw)
            ?.putString(KEY_LOGIN, next.loginPath)
            ?.putString(KEY_ACCOUNT, next.account.trim())
            ?.putBoolean(KEY_BLOCK_ON, next.block.enabled)
            ?.putString(KEY_BLOCK_RAW, next.block.raw)
        // 内置模式下不覆盖自定义路径，方便随时切回去
        if (!next.builtin) {
            e?.putString(KEY_HOME, next.homePath)
                ?.putString(KEY_CATS, next.categoriesPath)
                ?.putString(KEY_RANK, next.rankingPath)
                ?.putString(KEY_SEARCH, next.searchPath)
                ?.putString(KEY_DETAIL, next.detailPath)
                ?.putString(KEY_NOTICE, next.announcementsPath)
        }
        e?.apply()
    }

    fun applyNow() {
        version++
    }

    fun saveAndApply(next: ApiConfig) {
        save(next)
        applyNow()
    }

    fun reset() {
        config = ApiConfig()
        prefs?.edit()?.clear()?.apply()
    }

    private const val KEY_BUILTIN = "use_builtin"
    private const val KEY_USE_CUSTOM = "use_custom"
    private const val KEY_BASE = "base_url"
    private const val KEY_TOKEN = "token"
    private const val KEY_TOKEN_RAW = "token_raw"
    private const val KEY_LOGIN = "path_login"
    private const val KEY_ACCOUNT = "account"
    private const val KEY_HOME = "path_home"
    private const val KEY_CATS = "path_categories"
    private const val KEY_RANK = "path_ranking"
    private const val KEY_SEARCH = "path_search"
    private const val KEY_DETAIL = "path_detail"
    private const val KEY_NOTICE = "path_announcements"
    private const val KEY_BLOCK_ON = "block_enabled"
    private const val KEY_BLOCK_RAW = "block_raw"
    private const val KEY_DARK = "dark_mode"
    private const val KEY_COMPAT = "compat_mode"
}
