package com.picacg.coui.data

import kotlinx.coroutines.delay

/**
 * 示例数据实现。
 *
 * 接入真实数据时不要改这个文件，另写一个类实现 [ContentRepository]，
 * 然后在 [AppContainer] 里替换掉即可。
 */
class FakeRepository : ContentRepository {

    /**
     * 示例数据也配一张真实封面，这样没接自己的接口时也能看到图片加载效果。
     * picsum.photos 是个公开的占位图服务，同一个 seed 永远返回同一张图。
     * 网络不通时会自动退回渐变占位块，不影响布局。
     */
    private fun demoCover(id: String) = "https://picsum.photos/seed/$id/400/560"

    private val seed = listOf(
        "夜色下的城市" to "城市摄影",
        "山海之间" to "风景",
        "旧书店的午后" to "生活",
        "雨停之后" to "街拍",
        "深林的呼吸" to "自然",
        "海边的一整天" to "旅行",
        "老厂房改造记" to "建筑",
        "凌晨四点的街" to "纪实",
        "午后的猫" to "日常",
        "远方来信" to "随笔",
        "天台的夏天" to "青春",
        "雪落无声" to "冬季",
    )

    override suspend fun home(page: Int): FeedPage {
        delay(400) // 模拟网络耗时，方便看到加载态
        val perPage = 6
        val start = (page - 1) * perPage
        val slice = seed.drop(start).take(perPage)
        if (slice.isEmpty()) return FeedPage(emptyList(), hasMore = false)
        return FeedPage(
            items = slice.mapIndexed { i, (t, s) ->
                CardItem(
                    id = "p${start + i}",
                    title = t,
                    subtitle = s,
                    coverUrl = demoCover("p${start + i}"),
                    badge = if (i % 3 == 0) "新" else "",
                    tags = listOf(s),
                )
            },
            hasMore = start + perPage < seed.size,
        )
    }

    override suspend fun categories(): List<String> {
        delay(150)
        return listOf("全部", "摄影", "风景", "生活", "建筑", "纪实", "旅行")
    }

    /**
     * 示例数据的分类筛选：按 seed 里第二列的标签匹配。
     * 「全部」直接走普通分页，保证首页行为与真实接口一致。
     */
    override suspend fun homeByCategory(page: Int, category: String): FeedPage {
        if (category.isBlank() || category == "全部") return home(page)
        delay(300)
        // 用包含而不是相等：分类「摄影」要能匹配到 seed 里的「城市摄影」
        val matched = seed.filter { it.second.contains(category) || category.contains(it.second) }
        if (matched.isEmpty()) return FeedPage(emptyList(), hasMore = false)
        return FeedPage(
            items = matched.mapIndexed { i, (t, s) ->
                CardItem(
                    id = "c-$category-$i",
                    title = t,
                    subtitle = s,
                    coverUrl = demoCover("c-$category-$i"),
                    tags = listOf(s),
                )
            },
            hasMore = false,
        )
    }

    override suspend fun ranking(kind: String): List<CardItem> {
        delay(300)
        return seed.mapIndexed { i, (t, s) ->
            CardItem(
                id = "r$i",
                title = t,
                subtitle = s,
                coverUrl = demoCover("r$i"),
                badge = "${i + 1}",
                tags = listOf(s),
            )
        }.take(8)
    }

    override suspend fun search(keyword: String): List<CardItem> {
        delay(300)
        if (keyword.isBlank()) return emptyList()
        return seed.filter { it.first.contains(keyword) || it.second.contains(keyword) }
            .mapIndexed { i, (t, s) ->
                CardItem(
                    id = "s$i", title = t, subtitle = s,
                    coverUrl = demoCover("s$i"), tags = listOf(s),
                )
            }
    }

    override suspend fun detail(id: String): DetailItem {
        delay(300)
        val picked = seed.firstOrNull { id.endsWith(seed.indexOf(it).toString()) } ?: seed.first()
        return DetailItem(
            id = id,
            title = picked.first,
            author = "示例作者",
            description = "这是一段示例简介，用于展示详情页的正文排版效果。" +
                "接入真实数据后，这里会显示实际的内容摘要。",
            coverUrl = demoCover("d$id"),
            tags = listOf(picked.second, "示例标签"),
            chapters = (1..8).map { Chapter(id = "$id-c$it", title = "第 ${it} 话", pages = 20 + it) },
            likes = 1280,
            views = 34000,
        )
    }

    override suspend fun announcements(): List<Announcement> {
        delay(200)
        return listOf(
            Announcement("a1", "示例公告一", "这是示例公告内容，用于展示公告页排版。", "2026-11-20"),
            Announcement("a2", "示例公告二", "接入真实数据后，此处显示实际公告。", "2026-11-18"),
        )
    }
}
