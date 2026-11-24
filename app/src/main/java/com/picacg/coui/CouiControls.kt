package com.picacg.coui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Item
import com.itos.coui.switch.CouiSwitchDefaults
import com.itos.coui.switch.Switch
import com.itos.coui.text.CouiTextKind
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.TopAppBarSurface
import com.itos.coui.text.Text as CouiText

/**
 * 二级页的统一头部：状态栏让位 + 52dp 行高 + 返回圆钮 + 18sp 标题。
 * 与 ColorOS 的二级页一致；右侧 [actions] 放文字按钮或图标。
 *
 * 放在 `BackdropRoot` 里时用 COUI 的 `TopAppBarSurface` 画顶栏，
 * 滚动正文经过顶栏下方会有渐进模糊；没有 backdrop（低版本降级）时退回纯色。
 */
@Composable
fun PageHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scheme = CouiTheme.colorScheme
    val backdrop = LocalBackdrop.current
    val bar: @Composable (@Composable () -> Unit) -> Unit = { inner ->
        if (backdrop != null) {
            TopAppBarSurface(backdrop = backdrop) { inner() }
        } else {
            Box(Modifier.background(scheme.background)) { inner() }
        }
    }
    bar {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 52.dp)
                .padding(start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = BackArrowIcon,
                        contentDescription = "返回",
                        tint = scheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            actions()
        }
    }
}

/**
 * 设置项行：标题 + 说明 + 右侧当前值 + 箭头。
 * 直接用 COUI 的 Item，行高、内边距、按压反馈都由组件负责。
 */
@Composable
fun CouiSettingRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    iconSize: androidx.compose.ui.unit.Dp = CouiPageDefaults.iconSize,
) {
    val colors = CouiPageDefaults.themeColors()
    Item(
        icon = icon,
        iconSize = iconSize,
        onClick = onClick,
        enabled = enabled,
        title = {
            CouiText(
                text = title,
                kind = CouiTextKind.ItemTitle,
                color = if (enabled) colors.title else colors.disabled,
                textAlign = TextAlign.Start,
            )
        },
        summary = summary?.takeIf { it.isNotBlank() }?.let { text ->
            @Composable {
                CouiText(
                    text = text,
                    kind = CouiTextKind.ItemSummary,
                    color = if (enabled) colors.summary else colors.disabled,
                    textAlign = TextAlign.Start,
                )
            }
        },
        trailing = {
            if (!value.isNullOrBlank()) {
                CouiText(
                    text = value,
                    kind = CouiTextKind.ItemValue,
                    color = colors.summary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.widthIn(max = 180.dp).padding(start = 12.dp),
                )
            }
            if (showChevron) {
                Icon(
                    imageVector = ChevronIcon,
                    contentDescription = null,
                    tint = colors.summary.copy(alpha = 0.55f),
                    modifier = Modifier.padding(start = 6.dp).size(15.dp),
                )
            }
        },
    )
}

/**
 * 整行开关：点击行任意位置切换，开关本身只负责绘制。
 * 交互源共用，按压遮罩与开关滑块的手感一致。
 */
@Composable
fun CouiSwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val source = remember { MutableInteractionSource() }
    Item(
        modifier = Modifier
            .couiButtonFeedback(dark, source, enabled, list = true)
            .toggleable(checked, source, null, enabled, Role.Switch) { onCheckedChange(it) },
        title = {
            CouiText(
                text = title,
                kind = CouiTextKind.ItemTitle,
                color = if (enabled) colors.title else colors.disabled,
                textAlign = TextAlign.Start,
            )
        },
        summary = summary?.takeIf { it.isNotBlank() }?.let { text ->
            @Composable {
                CouiText(
                    text = text,
                    kind = CouiTextKind.ItemSummary,
                    color = if (enabled) colors.summary else colors.disabled,
                    textAlign = TextAlign.Start,
                )
            }
        },
        trailing = {
            Switch(
                modifier = Modifier.padding(start = 16.dp),
                checked = checked,
                onCheckedChange = null,
                dark = dark,
                colors = CouiSwitchDefaults.colors(colors.accent, dark),
                enabled = enabled,
                interactionSource = source,
                onDragCheckedChange = onCheckedChange,
            )
        },
    )
}

/** 组内说明文字，左右与 Item 的 16dp 对齐。 */
@Composable
fun CouiHint(text: String, color: Color = CouiTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = color,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** 组内衬一块内容（输入框、说明段落、按钮），保持 16dp 内边距。 */
@Composable
fun CouiGroupBody(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        content()
    }
}

/** 细箭头，用于可跳转的设置行。 */
val ChevronIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Chevron",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        ) {
            moveTo(9.5f, 5.5f)
            lineTo(16f, 12f)
            lineTo(9.5f, 18.5f)
        }
    }.build()
}
