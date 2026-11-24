package com.picacg.coui.data

/**
 * 数据层抽象接口。
 *
 * 界面只依赖这个接口，不关心数据从哪里来。
 * 接入你自己的后端时：新建一个类实现它，然后在 AppContainer 里替换掉 FakeRepository 即可。
 *
 * 所有方法都是 suspend，实现里请自行切到 IO 线程（如 withContext(Dispatchers.IO)）。
 */
interface ContentRepository {

    /** 首页信息流。page 从 1 开始。 */
    suspend fun home(page: Int): FeedPage

    /**
     * 按分类取一页。
     *
     * 默认实现直接忽略分类、等价于 [home]，这样老接口不用改也能用。
     * [HttpRepository] 会覆盖它：接口路径里写了 `{category}` 占位符就带上分类请求，
     * 没写就退回不带分类的请求（界面再按标签本地筛一遍兜底）。
     */
    suspend fun homeByCategory(page: Int, category: String): FeedPage = home(page)

    /** 分类列表，用于分类页的筛选标签。 */
    suspend fun categories(): List<String>

    /** 排行榜。kind 传 "day" / "week" / "month"。 */
    suspend fun ranking(kind: String): List<CardItem>

    /** 按关键词搜索。 */
    suspend fun search(keyword: String): List<CardItem>

    /** 详情页。 */
    suspend fun detail(id: String): DetailItem

    /** 公告列表。 */
    suspend fun announcements(): List<Announcement>
}

/** 一页信息流。hasMore 决定是否继续下拉加载。 */
data class FeedPage(
    val items: List<CardItem>,
    val hasMore: Boolean,
    /** 被屏蔽规则拦掉的条数，界面用来显示「已屏蔽 N 部」。 */
    val blocked: Int = 0,
)

/** 列表卡片。coverUrl 为空时界面会画占位色块，方便你接数据前先看布局。 */
data class CardItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val coverUrl: String = "",
    val badge: String = "",
    val tags: List<String> = emptyList(),
)

/** 详情页数据。 */
data class DetailItem(
    val id: String,
    val title: String,
    val author: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val tags: List<String> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val likes: Int = 0,
    val views: Int = 0,
)

data class Chapter(
    val id: String,
    val title: String,
    val pages: Int = 0,
)

data class Announcement(
    val id: String,
    val title: String,
    val content: String,
    val date: String = "",
)

/**
 * 全局依赖入口。
 *
 * 数据源是动态选择的：设置页里填了接口根地址就用 [HttpRepository] 走真实接口，
 * 没填就用 [FakeRepository] 的示例数据。因此不需要改代码、不需要重新编译。
 *
 * 除此之外这里还统一套一层屏蔽规则：所有列表/详情在返回前都会按
 * [ApiConfig.block] 过滤，界面层拿到的就已经是过滤后的数据，
 * 不用每个页面各写一遍。
 */
object AppContainer {

    /** 按当前设置返回数据源。 */
    fun repository(): ContentRepository {
        val cfg = ApiConfigStore.config
        return if (cfg.enabled) HttpRepository(cfg) else FakeRepository()
    }

    /** 兼容属性写法：AppContainer.repository。 */
    val repository: ContentRepository get() = repository()

    /** 当前是否已接上自定义接口。 */
    val usingRemote: Boolean get() = ApiConfigStore.config.enabled

    /** 当前生效的屏蔽规则。 */
    val blockRules: BlockRules get() = ApiConfigStore.config.block

    /** 按当前屏蔽规则过滤一批卡片，返回过滤后的列表与被拦掉的条数。 */
    fun filter(items: List<CardItem>): Pair<List<CardItem>, Int> {
        val rules = blockRules
        if (!rules.active) return items to 0
        val kept = items.filterNot { rules.blocks(it.id, it.fields()) }
        return kept to (items.size - kept.size)
    }

    /** 判断详情是否命中屏蔽规则。详情也会被拦，避免从排行等入口绕过。 */
    fun isBlocked(d: DetailItem): Boolean {
        val rules = blockRules
        if (!rules.active) return false
        return rules.blocks(
            d.id,
            mapOf(
                "title" to d.title,
                "author" to d.author,
                "subtitle" to d.description,
                "tags" to d.tags.joinToString(" / "),
            ),
        )
    }

    /**
     * 从列表里点「屏蔽此标签」时调用：追加一条 `tag:` 规则并立即落盘。
     *
     * 只保存、不 [ApiConfigStore.applyNow]，当前页由界面自己把命中的卡片摘掉，
     * 这样点一下不会让整页重新请求、闪一次 loading；之后翻页/重进由接口层统一过滤。
     *
     * @return true 表示这次真的新增了规则（标签已存在时返回 false）
     */
    fun blockTag(tag: String): Boolean {
        val cfg = ApiConfigStore.config
        val next = cfg.block.withTag(tag)
        if (next == cfg.block) return false
        ApiConfigStore.save(cfg.copy(block = next))
        return true
    }

    /** 撤销某个标签的屏蔽。 */
    fun unblockTag(tag: String) {
        val cfg = ApiConfigStore.config
        ApiConfigStore.save(cfg.copy(block = cfg.block.withoutTag(tag)))
    }
}

/** 卡片上所有能被关键词命中的文本，键名与 [BlockRules] 的维度名一致。 */
private fun CardItem.fields(): Map<String, String> = buildMap {
    put("title", title)
    put("subtitle", subtitle)
    put("tags", tags.joinToString(" / "))
    // author 维度在列表里没有独立字段，副标题常带作者名，这里让 author: 规则也能命中
    put("author", subtitle)
}

