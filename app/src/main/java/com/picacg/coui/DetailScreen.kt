package com.picacg.coui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picacg.coui.data.ApiConfigStore
import com.picacg.coui.data.AppContainer
import com.picacg.coui.data.DetailItem
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.TopAppBar

/** 详情页：封面、标题、标签、章节目录。对应哔咔的详情页。 */
@Composable
fun DetailScreen(id: String, onBack: () -> Unit) {
    var detail by remember { mutableStateOf<DetailItem?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var showChapterDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id, ApiConfigStore.version, reload) {
        loading = true
        error = null
        val r = runCatching { AppContainer.repository().detail(id) }
        error = r.exceptionOrNull()?.message
        // HttpRepository 已经在详情里拦过屏蔽内容，这里再兜一次底覆盖 FakeRepository
        detail = r.getOrNull()?.takeUnless { AppContainer.isBlocked(it) }
        if (detail == null && error == null) error = "该内容已被你的屏蔽规则拦截"
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    detail?.title ?: "详情",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                // 文字「返回」在深色下颜色偏淡，而且占的宽度不固定，
                // 换成箭头图标 + 40dp 圆形触控区（Material 的最小点击尺寸）
                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = BackIcon,
                        contentDescription = "返回",
                        tint = CouiTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )

        if (loading) {
            LoadingBlock("正在加载详情…")
        } else {
            val d = detail
            if (d == null) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    ErrorBlock(
                        error ?: "接口没有返回可用的详情数据",
                        onRetry = { reload++ },
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    overscrollEffect = rememberCouiOverscrollEffect(),
                ) {
                    item {
                        // 封面与基本信息并排：封面上方对齐，右侧文字一个 Column 到底，
                        // 标题最多两行，不会把下面的标签、统计挤出去。
                        // 外面套 Panel 是为了和下方「简介」「章节」保持同一种玻璃卡片，
                        // 之前这块是裸在背景上的，视觉上是断开的
                        Panel {
                        Row(verticalAlignment = Alignment.Top) {
                            CoverBlock(d.id, 104.dp, 140.dp, url = d.coverUrl)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    d.title,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CouiTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 25.sp,
                                )
                                if (d.author.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        d.author,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = CouiTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (d.tags.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        d.tags.take(3).forEach { TagChip(it) }
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    StatBlock("${d.views}", "浏览")
                                    StatBlock("${d.likes}", "喜欢")
                                    StatBlock("${d.chapters.size}", "章节")
                                }
                            }
                        }
                        }
                    }
                    item {
                        Panel {
                            SectionLabel("简介")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                d.description.ifBlank { "暂无简介" },
                                fontSize = 13.sp,
                                color = CouiTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "章节 · ${d.chapters.size}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CouiTheme.colorScheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "从第一话开始",
                                fontSize = 13.sp,
                                maxLines = 1,
                                color = CouiTheme.colorScheme.accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CouiTheme.colorScheme.accent.copy(alpha = 0.10f))
                                    .clickable {
                                        showChapterDialog = d.chapters.firstOrNull()?.title
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                    items(d.chapters) { ch ->
                        Panel(modifier = Modifier.clickable { showChapterDialog = ch.title }) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(8.dp).clip(CircleShape)
                                            .background(CouiTheme.colorScheme.accent.copy(alpha = 0.6f)),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        ch.title,
                                        fontSize = 15.sp,
                                        color = CouiTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "${ch.pages} 页",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    color = CouiTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    showChapterDialog?.let { title ->
        Dialog(
            onDismissRequest = { showChapterDialog = null },
            confirmAction = CouiDialogAction(
                text = "开始阅读",
                onClick = { showChapterDialog = null },
                style = CouiDialogActionStyle.Primary,
                colors = CouiDialogDefaults.primaryActionColors(CouiTheme.colorScheme.accent),
            ),
            dismissAction = CouiDialogAction(text = "取消", onClick = { showChapterDialog = null }),
            title = title,
            message = "阅读器界面为占位。接入真实数据后，这里会跳转到你实现的阅读页。",
            placement = com.itos.coui.dialog.CouiDialogPlacement.Center,
        )
    }
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            color = CouiTheme.colorScheme.onSurface)
        Text(label, fontSize = 11.sp, color = CouiTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 返回箭头。material-icons-core 里只有二十来个图标，没有 ArrowBack，
 * 这里按 Material 官方的 arrow_back 路径手画一个，尺寸与系统图标一致。
 */
private val BackIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Back",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 11f)
            lineTo(7.83f, 11f)
            lineTo(13.42f, 5.41f)
            lineTo(12f, 4f)
            lineTo(4f, 12f)
            lineTo(12f, 20f)
            lineTo(13.41f, 18.59f)
            lineTo(7.83f, 13f)
            lineTo(20f, 13f)
            close()
        }
    }.build()
}
