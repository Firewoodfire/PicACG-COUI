package com.picacg.coui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picacg.coui.data.Announcement
import com.picacg.coui.data.ApiConfigStore
import com.picacg.coui.data.AppContainer
import com.picacg.coui.data.CardItem
import com.itos.coui.chip.ChipTabRow
import com.itos.coui.chip.CouiChipTabItem
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.segmentbutton.SegmentedControl
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.theme.CouiTheme

/**
 * 主页：搜索栏 + 分类胶囊行 + 双列封面网格。
 *
 * 分页是滚到底自动加载，不设「加载更多」按钮：格子里最后三行露出来就开始拉下一页，
 * 到底了才在底部写一句「已经到底了」。切换分类会重置回第 1 页，
 * 因为不同分类的总页数并不通用。
 */
@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    var cats by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedCat by remember { mutableStateOf("全部") }
    var items by remember { mutableStateOf<List<CardItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(false) }
    var blocked by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var reload by remember { mutableIntStateOf(0) }

    // 搜索：关键字非空时进入搜索结果态，清空后自动回到信息流
    var keyword by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<CardItem>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    // 搜索结果里点标签屏蔽后，提示条 + 撤销；searchReload 用来在撤销后重搜一次
    var searchBlockedTag by remember { mutableStateOf<String?>(null) }
    var searchReload by remember { mutableIntStateOf(0) }

    // 首屏。三个触发来源：设置页保存配置、手动重试、切换分类——
    // 切换分类要重置到第 1 页，所以放在同一个 effect 里
    LaunchedEffect(ApiConfigStore.version, reload, selectedCat) {
        loading = true
        error = null
        page = 1
        hasMore = false
        blocked = 0
        val repo = AppContainer.repository()
        if (cats.isEmpty()) {
            cats = runCatching { repo.categories() }.getOrDefault(emptyList())
        }
        val r = runCatching { repo.homeByCategory(1, selectedCat) }
        error = r.exceptionOrNull()?.message
        val feed = r.getOrNull()
        items = feed?.items ?: emptyList()
        hasMore = feed?.hasMore ?: false
        blocked = feed?.blocked ?: 0
        loading = false
    }

    // 换关键词就收起上一轮的「已屏蔽」提示，避免和当前结果对不上
    LaunchedEffect(submitted) {
        searchBlockedTag = null
    }

    // 提交搜索
    LaunchedEffect(submitted, ApiConfigStore.version, searchReload) {
        val q = submitted.trim()
        if (q.isEmpty()) {
            results = emptyList()
            searchError = null
            return@LaunchedEffect
        }
        searching = true
        val r = runCatching { AppContainer.repository().search(q) }
        searchError = r.exceptionOrNull()?.message
        results = r.getOrDefault(emptyList())
        searching = false
    }

    // 滚到底自动加载下一页。留 3 个格子的余量，用户几乎感觉不到等待
    val gridState = rememberLazyGridState()
    // 滚动到顶/底时的回弹效果，用的是 COUI 自己那套，跟组件库其他页面手感一致
    val overscroll = rememberCouiOverscrollEffect()
    val nearBottom by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }

    LaunchedEffect(nearBottom, hasMore, loading) {
        if (!nearBottom || !hasMore || loading || loadingMore) return@LaunchedEffect
        loadingMore = true
        val next = page + 1
        val r = runCatching { AppContainer.repository().homeByCategory(next, selectedCat) }
        val feed = r.getOrNull()
        if (feed == null) {
            // 下一页失败就把「还有更多」关掉，避免滚动时反复重试同一个坏请求
            hasMore = false
        } else {
            // 去重：有些接口在超过最后一页后会把第一页原样返回
            val seen = items.mapTo(HashSet()) { it.id }
            val fresh = feed.items.filter { it.id !in seen }
            items = items + fresh
            blocked += feed.blocked
            page = next
            hasMore = feed.hasMore && fresh.isNotEmpty()
        }
        loadingMore = false
    }

    Column(Modifier.fillMaxSize()) {
        SearchField(
            value = keyword,
            onValueChange = { keyword = it },
            onSearch = { submitted = keyword },
            onClear = {
                keyword = ""
                submitted = ""
            },
        )

        when {
            submitted.isNotBlank() -> SearchResults(
                keyword = submitted,
                items = results,
                loading = searching,
                error = searchError,
                onOpen = onOpen,
                blockedTag = searchBlockedTag,
                onBlockTag = { tag ->
                    AppContainer.blockTag(tag)
                    // 先把当前结果里命中的摘掉，撤销时再靠重搜恢复
                    results = results.filterNot { c ->
                        c.tags.any { it.equals(tag, ignoreCase = true) }
                    }
                    searchBlockedTag = tag
                },
                onUndoBlock = {
                    searchBlockedTag?.let { AppContainer.unblockTag(it) }
                    searchBlockedTag = null
                    searchReload++
                },
            )

            loading -> LoadingBlock("正在加载主页…")

            error != null -> Box(Modifier.fillMaxSize().padding(16.dp)) {
                ErrorBlock(error!!, onRetry = { reload++ })
            }

            items.isEmpty() -> EmptyBlock(
                if (selectedCat == "全部") "还没有内容" else "「$selectedCat」下没有内容",
                if (selectedCat == "全部") {
                    "当前接口没有返回任何条目，可在「设置」里检查路径。"
                } else {
                    "换个分类看看；如果接口支持分类参数，把 {category} 写进首页路径。"
                },
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                overscrollEffect = overscroll,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 108.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (cats.isNotEmpty() || blocked > 0) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            if (cats.isNotEmpty()) {
                                CategoryRow(
                                    cats = cats,
                                    selected = selectedCat,
                                    onSelect = { selectedCat = it },
                                )
                            }
                            if (blocked > 0) BlockedHint(blocked)
                        }
                    }
                }
                items(items, key = { it.id }) { item ->
                    PosterCard(item = item, onClick = { onOpen(item.id) })
                }
                // 底部状态：加载中 / 到底了。两种都不占高度时给个小间隔
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            loadingMore -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    color = CouiTheme.colorScheme.accent,
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    "正在加载更多…",
                                    fontSize = 12.sp,
                                    color = CouiTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            !hasMore && items.size > 4 -> Text(
                                "已经到底了",
                                fontSize = 12.sp,
                                color = CouiTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分类胶囊行。用 COUI 的 [ChipTabRow]：整行可横向滑动，
 * 选中项由一块跟着手指/选中位置滑动的胶囊指示器标出，比一排各自独立的胶囊更像系统组件。
 *
 * Android 11 及以下没有 backdrop（renderEffect 是 API 31 才有的），
 * 而 ChipTabRow 的 backdrop 参数要求非空，这时退回自绘的 [FilterChip]。
 */
@Composable
private fun CategoryRow(
    cats: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val backdrop = LocalBackdrop.current
    // 分类是异步拉的：首帧 cats 还是空的时候，ChipTabRow 会因
    // 「选中项不属于当前列表」直接抛 require 异常（一点开就闪退的真凶）。
    // 空列表先走下面的普通 Row，等分类回来再换回胶囊行。
    if (backdrop != null && cats.isNotEmpty()) {
        val chipItems = remember(cats) { cats.map { CouiChipTabItem(key = it, label = it) } }
        // 选中的分类如果不在新列表里，传 null 让组件自己兜住，也不崩
        val safeKey = chipItems.firstOrNull { it.key == selected }?.key
        ChipTabRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            items = chipItems,
            selectedKey = safeKey,
            onSelectedKeyChange = { onSelect(it.toString()) },
            backdrop = backdrop,
            dark = LocalDarkMode.current,
        )
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cats.forEach { c ->
                FilterChip(text = c, selected = c == selected, onClick = { onSelect(c) })
            }
        }
    }
}

/**
 * 搜索结果。标题区说明搜了什么、命中多少，列表复用 [ItemRow]。
 * 空关键字不会走到这里——调用方已经回到信息流。
 */
@Composable
private fun SearchResults(
    keyword: String,
    items: List<CardItem>,
    loading: Boolean,
    error: String?,
    onOpen: (String) -> Unit,
    blockedTag: String? = null,
    onBlockTag: ((String) -> Unit)? = null,
    onUndoBlock: () -> Unit = {},
) {
    val scheme = CouiTheme.colorScheme
    val overscroll = rememberCouiOverscrollEffect()
    var pendingTag by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 6.dp, bottom = 6.dp)
                .heightIn(min = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "搜索结果",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (loading) "搜索中…" else "${items.size} 部",
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
            )
        }
        when {
            loading -> LoadingBlock("正在搜索「$keyword」…")
            error != null -> Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                ErrorBlock(error)
            }
            items.isEmpty() -> EmptyBlock(
                "没找到「$keyword」",
                "换个关键词试试；如果确实不该为空，检查设置里的搜索路径 {keyword}。",
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                overscrollEffect = overscroll,
            ) {
                blockedTag?.let { tag ->
                    item { BlockedTagTip(tag = tag, onUndo = onUndoBlock) }
                }
                items(items, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        onClick = { onOpen(item.id) },
                        coverWidth = 56.dp,
                        coverHeight = 76.dp,
                        onBlockTag = if (onBlockTag != null) {
                            { tag -> pendingTag = tag }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    pendingTag?.let { tag ->
        BlockTagDialog(
            tag = tag,
            onDismiss = { pendingTag = null },
            onConfirm = {
                onBlockTag?.invoke(tag)
                pendingTag = null
            },
        )
    }
}

/** 「已屏蔽 N 部」的小提示，点一下能去设置里改规则。 */
@Composable
private fun BlockedHint(count: Int) {
    Row(
        Modifier
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CouiTheme.colorScheme.container.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "已按屏蔽规则隐藏 $count 部",
            fontSize = 11.sp,
            color = CouiTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}


/**
 * 搜索栏。照着 ColorOS / COUI 组件库里那个胶囊搜索框画的：
 * 深色圆角胶囊 + 放大镜 + 灰色提示文字，输入后右侧出现清除按钮，
 * 键盘回车或点放大镜触发搜索。宽度撑满，左右各留 16dp，和分组卡片对齐。
 *
 * COUI 库里其实有 `com.itos.coui.search.SearchBar`，但它是会展开成全屏搜索页的
 * 交互组件，需要配 CouiBackdrop 才能正常渲染，这里只想要输入框本身，所以自绘。
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    val scheme = CouiTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
            .clip(shape)
            .background(scheme.container.copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            imageVector = SearchIcon,
            contentDescription = "搜索",
            tint = scheme.onSurfaceVariant,
            modifier = Modifier
                .size(21.dp)
                .clickable { onSearch() },
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    "输入漫画关键词",
                    fontSize = 15.sp,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            // 清除按钮：一个小圆底叉号
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(scheme.onSurfaceVariant.copy(alpha = 0.35f))
                    .clickable { onClear() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "×",
                    fontSize = 13.sp,
                    color = scheme.container,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** 放大镜图标。material-icons-core 里没有 Search，手画一个。 */
private val SearchIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Search",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 圆环
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.1f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        ) {
            moveTo(10.5f, 3f)
            curveTo(14.6f, 3f, 18f, 6.4f, 18f, 10.5f)
            curveTo(18f, 14.6f, 14.6f, 18f, 10.5f, 18f)
            curveTo(6.4f, 18f, 3f, 14.6f, 3f, 10.5f)
            curveTo(3f, 6.4f, 6.4f, 3f, 10.5f, 3f)
            close()
        }
        // 手柄
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.1f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
        ) {
            moveTo(15.6f, 15.6f)
            lineTo(20.5f, 20.5f)
        }
    }.build()
}


@Composable
private fun PosterCard(item: CardItem, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }) {
        Box {
            CoverBlock(
                seed = item.id,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
                corner = 16.dp,
                url = item.coverUrl,
            )
            if (item.badge.isNotEmpty()) {
                Box(Modifier.padding(8.dp)) { Badge(item.badge) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = CouiTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp,
        )
        if (item.subtitle.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                item.subtitle,
                fontSize = 12.sp,
                color = CouiTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 横向筛选胶囊，主页与排行页共用。 */
@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = CouiTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) scheme.accent else scheme.container.copy(alpha = 0.7f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            fontSize = 13.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onAccent else scheme.onSurfaceVariant,
        )
    }
}

@Composable
fun Badge(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CouiTheme.colorScheme.accent)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = CouiTheme.colorScheme.onAccent)
    }
}

/**
 * 标签胶囊。
 *
 * 传了 [onClick] 就能点：列表里点一下会弹「屏蔽此标签」确认框，
 * 这是最直观的屏蔽入口——用户看见讨厌的标签，不用去设置里手敲 `tag:`。
 */
@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CouiTheme.colorScheme.accent.copy(alpha = 0.10f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 22.dp)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = CouiTheme.colorScheme.accent,
        )
    }
}

/**
 * 「屏蔽此标签」确认框。
 * 文案写清影响范围，避免用户以为只屏蔽当前这一部。
 */
@Composable
fun BlockTagDialog(
    tag: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        title = "屏蔽标签「$tag」？",
        message = "标题或标签里带「$tag」的内容都会从列表、搜索和排行里隐藏。" +
            "想取消时，到 设置 → 屏蔽 里点掉这枚标签即可。",
        confirmAction = CouiDialogAction(
            text = "屏蔽",
            onClick = onConfirm,
            style = CouiDialogActionStyle.Primary,
            colors = CouiDialogDefaults.primaryActionColors(CouiTheme.colorScheme.accent),
        ),
        dismissAction = CouiDialogAction(text = "取消", onClick = onDismiss),
        placement = com.itos.coui.dialog.CouiDialogPlacement.Center,
    )
}

/**
 * 屏蔽成功后的一次性提示条，带撤销。
 *
 * 放在列表顶部而不是做成自动消失的 Snackbar：不会挡住最后一行，
 * 也不会因为用户手快没看清就再也找不回撤销入口。
 */
@Composable
fun BlockedTagTip(tag: String, onUndo: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CouiTheme.colorScheme.accent.copy(alpha = 0.10f))
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "已屏蔽标签「$tag」",
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = CouiTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            "撤销",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = CouiTheme.colorScheme.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onUndo)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/** 分类页：左侧分类栏 + 右侧列表。对应哔咔「分类」栏目。 */
@Composable
fun CategoryScreen(onOpen: (String) -> Unit) {
    var cats by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<CardItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var blocked by remember { mutableIntStateOf(0) }

    // 点标签屏蔽：pendingTag 是待确认的标签，blockedTag 是刚屏蔽完、可以撤销的那一枚
    var pendingTag by remember { mutableStateOf<String?>(null) }
    var blockedTag by remember { mutableStateOf<String?>(null) }

    // selected 也是 key：以前点左边的分类只改了高亮，右边的列表根本没变，
    // 现在切分类会真的按分类重新拉一页
    LaunchedEffect(ApiConfigStore.version, reload, selected) {
        loading = true
        error = null
        blockedTag = null
        val repo = AppContainer.repository()
        if (cats.isEmpty()) {
            cats = runCatching { repo.categories() }.getOrDefault(emptyList()).filter { it != "全部" }
        }
        val cat = cats.getOrNull(selected)
        val page = runCatching {
            if (cat.isNullOrBlank()) repo.home(1) else repo.homeByCategory(1, cat)
        }
        error = page.exceptionOrNull()?.message
        items = page.getOrNull()?.items ?: emptyList()
        blocked = page.getOrNull()?.blocked ?: 0
        loading = false
    }

    val current = cats.getOrNull(selected)
    val overscroll = rememberCouiOverscrollEffect()
    Row(Modifier.fillMaxSize()) {
        if (cats.isNotEmpty()) {
            Column(
                Modifier
                    .width(92.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 8.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                cats.forEachIndexed { i, c ->
                    val on = i == selected
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (on) CouiTheme.colorScheme.accent.copy(alpha = 0.14f)
                                else Color.Transparent
                            )
                            .clickable { selected = i }
                            .padding(vertical = 13.dp, horizontal = 10.dp),
                    ) {
                        Text(
                            c,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) CouiTheme.colorScheme.accent
                            else CouiTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        when {
            loading -> LoadingBlock()
            error != null -> Box(Modifier.fillMaxSize().padding(end = 16.dp)) {
                ErrorBlock(error!!, onRetry = { reload++ })
            }
            else -> LazyColumn(
                Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                overscrollEffect = overscroll,
            ) {
                item {
                    // 组标题行：与 COUI 分组标题同为 12sp 次级色，名字单独放大成小标题
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp, end = 32.dp, top = 6.dp, bottom = 6.dp)
                            .heightIn(min = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            current ?: "全部",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CouiTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            if (blocked > 0) "${items.size} 部 · 屏蔽 $blocked" else "${items.size} 部",
                            fontSize = 12.sp,
                            color = CouiTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (items.isEmpty()) {
                    item { EmptyBlock("这个分类还没有内容", "换一个分类看看，或在设置里调整接口路径。") }
                }
                blockedTag?.let { tag ->
                    item { BlockedTagTip(tag = tag, onUndo = {
                        AppContainer.unblockTag(tag)
                        blockedTag = null
                        reload++
                    }) }
                }
                items(items, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        onClick = { onOpen(item.id) },
                        coverWidth = 56.dp,
                        coverHeight = 76.dp,
                        onBlockTag = { pendingTag = it },
                    )
                }
            }
        }
    }

    pendingTag?.let { tag ->
        BlockTagDialog(
            tag = tag,
            onDismiss = { pendingTag = null },
            onConfirm = {
                AppContainer.blockTag(tag)
                // 当前页本地先摘掉，不重新请求，点一下不会闪 loading
                val removed = items.count { it.tags.any { t -> t.equals(tag, ignoreCase = true) } }
                items = items.filterNot { it.tags.any { t -> t.equals(tag, ignoreCase = true) } }
                blocked += removed
                pendingTag = null
                blockedTag = tag
            },
        )
    }
}

/**
 * 横向列表项：封面 + 标题 + 副标题 + 标签。
 * 文字列用 weight(1f) 撑满剩余宽度，长标题会省略而不是撑破卡片。
 *
 * 传了 [onBlockTag] 后，标题下面的标签变成可点：点一下弹确认框，
 * 确认后把这个标签写进屏蔽规则，列表里带该标签的条目会立刻消失。
 */
@Composable
fun ItemRow(
    item: CardItem,
    onClick: () -> Unit,
    coverWidth: androidx.compose.ui.unit.Dp = 64.dp,
    coverHeight: androidx.compose.ui.unit.Dp = 86.dp,
    leading: (@Composable () -> Unit)? = null,
    onBlockTag: ((String) -> Unit)? = null,
) {
    Group {
        Item(onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leading?.let {
                    it()
                    Spacer(Modifier.width(8.dp))
                }
                CoverBlock(item.id, coverWidth, coverHeight, url = item.coverUrl)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CouiTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                    )
                    if (item.subtitle.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            item.subtitle,
                            fontSize = 12.sp,
                            color = CouiTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (item.tags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // 一行最多三枚，长标签各自最多占三分之一宽，超出省略
                            item.tags.take(3).forEach { tag ->
                                TagChip(
                                    text = tag,
                                    modifier = Modifier.weight(1f, fill = false),
                                    onClick = onBlockTag?.let { block -> { block(tag) } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 排行榜页：名次 + 列表。对应哔咔「排行」栏目。 */
/** 排行榜的时间范围。顺序即分段选择器的顺序。 */
private val RANK_KINDS = listOf("day" to "日榜", "week" to "周榜", "month" to "月榜")

@Composable
fun RankingScreen(onOpen: (String) -> Unit) {
    // 默认选「周榜」，即列表第 2 项
    var kindIndex by remember { mutableIntStateOf(1) }
    val kind = RANK_KINDS[kindIndex].first
    var items by remember { mutableStateOf<List<CardItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var pendingTag by remember { mutableStateOf<String?>(null) }
    var blockedTag by remember { mutableStateOf<String?>(null) }

    val overscroll = rememberCouiOverscrollEffect()

    LaunchedEffect(kind, ApiConfigStore.version, reload) {
        loading = true
        error = null
        // 排行接口自己会过滤，这里再兜一次底，保证 FakeRepository 也遵守规则
        val r = runCatching { AppContainer.repository().ranking(kind) }
        error = r.exceptionOrNull()?.message
        items = AppContainer.filter(r.getOrDefault(emptyList())).first
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        // 用 COUI 的分段选择器（SegmentedControl），替掉原来的三个独立 FilterChip：
        // 外层是一整块胶囊容器，选中项是容器里滑动的实心胶囊，
        // 切换时有位移回弹动效，落点带触感反馈。
        //
        // 容器色与指示器色**故意不覆盖**，用组件按深浅色自动算的那一套：
        //   深色 → 容器 12% 白、指示器 20% 白（就是组件库演示页那个样子）
        //   浅色 → 容器 6% 黑、指示器 70% 白（iOS 那种灰底白滑块）
        // 因为组件内部文字统一取 onSurface，选中/未选中只调透明度，
        // 如果硬把指示器换成强调蓝，浅色下黑字压蓝底会看不清。
        SegmentedControl(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            labels = RANK_KINDS.map { it.second },
            selectedIndex = kindIndex,
            onSelected = { kindIndex = it },
            backdrop = LocalBackdrop.current,
            dark = LocalDarkMode.current,
        )
        // 分段选择器下方补一条组标题，和设置页、关于页的分组节奏一致
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 6.dp)
                .heightIn(min = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CouiText(
                text = "${RANK_KINDS[kindIndex].second} · ${items.size} 部",
                kind = CouiTextKind.GroupTitle,
                color = CouiTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            loading -> LoadingBlock("正在加载排行…")
            error != null -> Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                ErrorBlock(error!!, onRetry = { reload++ })
            }
            items.isEmpty() -> EmptyBlock("榜单是空的", "换个时间范围，或检查排行接口路径。")
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                overscrollEffect = overscroll,
            ) {
                blockedTag?.let { tag ->
                    item { BlockedTagTip(tag = tag, onUndo = {
                        AppContainer.unblockTag(tag)
                        blockedTag = null
                        reload++
                    }) }
                }
                items(items, key = { it.id }) { item ->
                    val rank = item.badge.toIntOrNull() ?: 0
                    ItemRow(
                        item = item,
                        onClick = { onOpen(item.id) },
                        coverWidth = 52.dp,
                        coverHeight = 70.dp,
                        leading = {
                            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    rank.toString(),
                                    fontSize = if (rank in 1..3) 19.sp else 15.sp,
                                    fontWeight = if (rank in 1..3) FontWeight.Bold else FontWeight.Medium,
                                    color = if (rank in 1..3) CouiTheme.colorScheme.accent
                                    else CouiTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onBlockTag = { pendingTag = it },
                    )
                }
            }
        }
    }

    pendingTag?.let { tag ->
        BlockTagDialog(
            tag = tag,
            onDismiss = { pendingTag = null },
            onConfirm = {
                AppContainer.blockTag(tag)
                items = items.filterNot { it.tags.any { t -> t.equals(tag, ignoreCase = true) } }
                pendingTag = null
                blockedTag = tag
            },
        )
    }
}

/**
 * 公告页。对应哔咔「公告」栏目。
 *
 * 之前的写法把标题、正文、日期塞进同一行，三者会互相压住。
 * 现在改成纵向：标题一行、日期一行、正文再一行。
 */
@Composable
fun NoticeScreen() {
    var list by remember { mutableStateOf<List<Announcement>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(ApiConfigStore.version, reload) {
        loading = true
        error = null
        val r = runCatching { AppContainer.repository().announcements() }
        error = r.exceptionOrNull()?.message
        list = r.getOrDefault(emptyList())
        loading = false
    }

    val overscroll = rememberCouiOverscrollEffect()

    when {
        loading -> LoadingBlock("正在加载公告…")
        error != null -> Box(Modifier.fillMaxSize().padding(16.dp)) {
            ErrorBlock(error!!, onRetry = { reload++ })
        }
        list.isEmpty() -> EmptyBlock("暂无公告", "接口没有返回公告内容，可稍后刷新或检查公告路径。")
        else -> LazyColumn(
            Modifier.fillMaxSize(),
            // 底部留 108dp：右下角悬浮的设置按钮会盖在内容上，公告卡片别被压住
            contentPadding = PaddingValues(bottom = 108.dp),
            overscrollEffect = overscroll,
        ) {
            items(list, key = { it.id }) { a ->
                Group {
                    CouiGroupBody {
                        Column {
                            Text(
                                a.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CouiTheme.colorScheme.onSurface,
                                lineHeight = 22.sp,
                            )
                            if (a.date.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                DateBadge(a.date)
                            }
                            if (a.content.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    a.content,
                                    fontSize = 13.sp,
                                    color = CouiTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 21.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 空状态：不显示空白页，给一句话说明。 */
@Composable
fun EmptyBlock(title: String, hint: String = "") {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CouiTheme.colorScheme.accent.copy(alpha = 0.10f)),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = CouiTheme.colorScheme.onSurface,
        )
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                hint,
                fontSize = 12.sp,
                color = CouiTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
    }
}
