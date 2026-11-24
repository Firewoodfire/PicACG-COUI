package com.picacg.coui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import com.itos.coui.theme.CouiTheme

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
        PageHeader(detail?.title ?: "详情", onBack = onBack)

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
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    overscrollEffect = rememberCouiOverscrollEffect(),
                ) {
                    item {
                        // 封面与基本信息并排：封面上方对齐，右侧文字一个 Column 到底，
                        // 标题最多三行，不会把下面的标签、统计挤出去。
                        // 外面套 Group 与下方「简介」「章节」保持同一种卡片
                        Group {
                            CouiGroupBody {
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
                    }
                    item {
                        Group(title = "简介") {
                            CouiGroupBody {
                                Text(
                                    d.description.ifBlank { "暂无简介" },
                                    fontSize = 13.sp,
                                    color = CouiTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 21.sp,
                                )
                            }
                        }
                    }
                    item {
                        Group(
                            title = "章节 · ${d.chapters.size}",
                            headerActions = {
                                Text(
                                    "从第一话开始",
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    color = CouiTheme.colorScheme.accent,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            showChapterDialog = d.chapters.firstOrNull()?.title
                                        }
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            },
                        ) {
                            if (d.chapters.isEmpty()) {
                                CouiGroupBody {
                                    Text(
                                        "这一话还没有可用章节",
                                        fontSize = 13.sp,
                                        color = CouiTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                d.chapters.forEachIndexed { index, ch ->
                                    if (index > 0) Divider()
                                    CouiSettingRow(
                                        title = ch.title,
                                        value = "${ch.pages} 页",
                                        showChevron = true,
                                        onClick = { showChapterDialog = ch.title },
                                    )
                                }
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
