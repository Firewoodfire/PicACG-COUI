package com.picacg.coui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.Dialog
import com.itos.coui.layout.Group
import com.itos.coui.theme.CouiTheme
import com.picacg.coui.data.StorageManager
import com.picacg.coui.data.StorageSnapshot

/** 作者名。 */
private const val AUTHOR = "菜菜"

/** 交流群群号。 */
private const val QQ_GROUP = "1121329450"

/**
 * 关于页。版式参照 ColorOS 的「关于此手机」：
 * 大标题 → 主视觉 → 作者 / 信息 / 群 / 各入口，全部用 COUI 的分组卡片。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpen: (AboutPage) -> Unit = {},
) {
    val scheme = CouiTheme.colorScheme
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val storage by produceState<StorageSnapshot?>(initialValue = null, ctx) {
        value = StorageManager.snapshot(ctx)
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("关于此应用", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)) {
                AboutHero()
                Spacer(Modifier.height(6.dp))
            }

            Group {
                CouiSettingRow(
                    title = AUTHOR,
                    summary = "作者",
                    iconSize = 52.dp,
                    icon = {
                        Image(
                            painter = painterResource(R.drawable.avatar_cai),
                            contentDescription = AUTHOR,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .border(1.dp, scheme.onSurface.copy(alpha = 0.10f), CircleShape),
                        )
                    },
                )
            }

            Group {
                CouiSettingRow("应用名", value = "PicACG · COUI 版")
                com.itos.coui.layout.Divider()
                CouiSettingRow("版本", value = "v${BuildConfig.VERSION_CODE} · ${BuildConfig.VERSION_NAME}")
                com.itos.coui.layout.Divider()
                CouiSettingRow("样式参考", value = "ColorOS 17")
            }

            Group {
                CouiSettingRow(
                    title = "QQ 交流群",
                    summary = QQ_GROUP,
                    value = "加入",
                    showChevron = true,
                    icon = { QqIcon(size = 28.dp, tint = Color(0xFF12B7F5)) },
                    onClick = {
                        val r = openQqGroup(ctx, QQ_GROUP)
                        if (!r.first) dialog = r.second to QQ_GROUP
                    },
                )
            }

            AboutActionCard(
                rows = listOf(
                    AboutAction(
                        label = "储存空间",
                        value = storage?.let { StorageManager.formatSize(it.totalBytes) } ?: "计算中…",
                        page = AboutPage.Storage,
                    ),
                ),
                onOpen = onOpen,
            )

            // 按截图的分组方式：信息类一张卡，计划与版本类另起一张卡；
            // 不放 ColorOS 共创者星球——这不是 ColorOS 系统应用，放进去会误导用户。
            AboutActionCard(
                rows = listOf(
                    AboutAction("版本信息", page = AboutPage.VersionInfo),
                    AboutAction("法律信息", page = AboutPage.Legal),
                    AboutAction("认证信息", page = AboutPage.Certification),
                    AboutAction("状态信息", page = AboutPage.Status),
                    AboutAction("使用说明", page = AboutPage.Help),
                    AboutAction("大模型明示告知", page = AboutPage.ModelNotice),
                ),
                onOpen = onOpen,
            )

            AboutActionCard(
                rows = listOf(
                    AboutAction("体验改进计划", page = AboutPage.Experience),
                    AboutAction("软件版本", value = BuildConfig.VERSION_NAME, page = AboutPage.SoftwareVersion),
                    AboutAction("硬件版本", page = AboutPage.HardwareVersion),
                ),
                onOpen = onOpen,
            )

            Spacer(Modifier.height(28.dp))
        }
    }

    dialog?.let { (title, group) ->
        Dialog(
            onDismissRequest = { dialog = null },
            confirmAction = CouiDialogAction(
                text = "复制群号",
                onClick = {
                    copyToClipboard(ctx, group)
                    dialog = null
                },
                style = com.itos.coui.dialog.CouiDialogActionStyle.Primary,
                colors = CouiDialogDefaults.primaryActionColors(scheme.accent),
            ),
            dismissAction = CouiDialogAction(text = "知道了", onClick = { dialog = null }),
            title = title,
            message = "群号：$group\n可以直接复制后在 QQ 里搜索加入。",
            placement = com.itos.coui.dialog.CouiDialogPlacement.Center,
        )
    }
}

/** 顶部主视觉。渐变 + 应用名，圆角 20dp，比例 1:1 略扁。 */
@Composable
private fun AboutHero() {
    val scheme = CouiTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.05f)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        scheme.accent.copy(alpha = 0.85f),
                        scheme.accentOnSurface.copy(alpha = 0.55f),
                        scheme.accent.copy(alpha = 0.30f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 两团柔光，让平面渐变不至于像没加载出来
        Box(
            Modifier
                .size(240.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .size(200.dp)
                .align(Alignment.BottomStart)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "PicACG",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "COUI · ColorOS 17",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * 返回箭头。material-icons-core 只带二十来个图标，没有 ArrowBack，
 * 这里按 Material 官方的 arrow_back 路径手画。
 */
internal val BackArrowIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ArrowBack",
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

/**
 * 企鹅轮廓。用椭圆拼出来的简化版：机身一个椭圆 + 两只脚 + 尖嘴，
 * 缩到 20dp 也还能看出是企鹅，不需要引入品牌素材。
 */
private val PenguinIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Penguin",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // 身体
            moveTo(12f, 2f)
            curveTo(8.1f, 2f, 5.2f, 5.3f, 5.2f, 9.4f)
            curveTo(5.2f, 11f, 4.4f, 12f, 3.8f, 13.3f)
            curveTo(3.5f, 10.9f, 2.7f, 10.5f, 2.3f, 10.9f)
            curveTo(1.9f, 11.3f, 2.3f, 13.9f, 3.6f, 15.1f)
            curveTo(3.9f, 17.6f, 5.5f, 19.4f, 5.5f, 19.4f)
            curveTo(5.1f, 20.4f, 5.3f, 21.3f, 6.1f, 21.6f)
            curveTo(6.9f, 21.9f, 7.7f, 21.3f, 8f, 20.5f)
            curveTo(8.6f, 20.8f, 10.2f, 21f, 12f, 21f)
            curveTo(13.8f, 21f, 15.4f, 20.8f, 16f, 20.5f)
            curveTo(16.3f, 21.3f, 17.1f, 21.9f, 17.9f, 21.6f)
            curveTo(18.7f, 21.3f, 18.9f, 20.4f, 18.5f, 19.4f)
            curveTo(18.5f, 19.4f, 20.1f, 17.6f, 20.4f, 15.1f)
            curveTo(21.7f, 13.9f, 22.1f, 11.3f, 21.7f, 10.9f)
            curveTo(21.3f, 10.5f, 20.5f, 10.9f, 20.2f, 13.3f)
            curveTo(19.6f, 12f, 18.8f, 11f, 18.8f, 9.4f)
            curveTo(18.8f, 5.3f, 15.9f, 2f, 12f, 2f)
            close()
            // 左眼
            moveTo(9.6f, 8.6f)
            curveTo(10.4f, 8.6f, 11f, 9.4f, 11f, 10.4f)
            curveTo(11f, 11.4f, 10.4f, 12.2f, 9.6f, 12.2f)
            curveTo(8.8f, 12.2f, 8.2f, 11.4f, 8.2f, 10.4f)
            curveTo(8.2f, 9.4f, 8.8f, 8.6f, 9.6f, 8.6f)
            close()
            // 右眼
            moveTo(14.4f, 8.6f)
            curveTo(15.2f, 8.6f, 15.8f, 9.4f, 15.8f, 10.4f)
            curveTo(15.8f, 11.4f, 15.2f, 12.2f, 14.4f, 12.2f)
            curveTo(13.6f, 12.2f, 13f, 11.4f, 13f, 10.4f)
            curveTo(13f, 9.4f, 13.6f, 8.6f, 14.4f, 8.6f)
            close()
            // 尖嘴
            moveTo(12f, 12.6f)
            lineTo(13.6f, 14f)
            lineTo(12f, 14.6f)
            lineTo(10.4f, 14f)
            close()
        }
    }.build()
}

/** QQ 图标底板。 */
@Composable
private fun QqIcon(size: androidx.compose.ui.unit.Dp, tint: Color) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = PenguinIcon,
            contentDescription = "QQ",
            tint = tint,
            modifier = Modifier.size(size * 0.62f),
        )
    }
}

// ------------------------------------------------------------------ 跳转 QQ

/**
 * 打开与某个 QQ 群的聊天窗口。
 *
 * 三条路依次试，因为不同版本/不同渠道的 QQ 支持的 scheme 不一样：
 *  1. mqqapi://card/show_pslcard?src_type=internal&card_type=group&uin=群号
 *     —— 官方 SDK 用的就是这个，能直接进群资料页并显示「申请加入」
 *  2. mqq://im/chat?chat_type=wpa&uin=群号 —— 老的加好友/加群入口，兼容旧版本
 *  3. 都失败时返回失败，交给上层弹「复制群号」的兜底
 *
 * 返回值：成功是 (true, "")，失败是 (false, 给用户看的原因)。
 */
private fun openQqGroup(
    ctx: android.content.Context,
    group: String,
): Pair<Boolean, String> {
    val candidates = listOf(
        "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$group&card_type=group&source=qrcode",
        "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$group&card_type=group",
        "mqq://im/chat?chat_type=wpa&uin=$group&version=1&src_type=web",
    )
    for (url in candidates) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            return true to ""
        } catch (_: ActivityNotFoundException) {
            // 这个 scheme 系统不认，继续试下一个
        } catch (_: SecurityException) {
            // 极少数 ROM 会拦截，同样当作不支持
        } catch (_: Exception) {
        }
    }
    // 最后试一次 https 的群邀请链接，微信/QQ 浏览器都可能接管
    try {
        ctx.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://qm.qq.com/cgi-bin/qm/qr?k=$group"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true to ""
    } catch (_: Exception) {
    }
    return false to "没有找到 QQ"
}

private fun copyToClipboard(ctx: android.content.Context, text: String) {
    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(android.content.ClipData.newPlainText("QQ 群号", text))
}
