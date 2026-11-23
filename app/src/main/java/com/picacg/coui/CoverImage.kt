package com.picacg.coui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.itos.coui.theme.CouiTheme
import com.picacg.coui.data.CoverLoader

/**
 * 封面图。有 [url] 就真的去下载并显示，没有或加载失败就退回渐变占位块。
 *
 * 加载走 [CoverLoader] 的三层缓存（内存 → 磁盘 → 网络），
 * 所以列表来回滚动时不会反复请求，也不会出现「先白一下再出图」。
 */
@Composable
fun CoverBlock(
    seed: String,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    url: String = "",
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.clip(RoundedCornerShape(corner))) {
        val wPx = with(density) { maxWidth.roundToPx() }
        val hPx = with(density) { maxHeight.roundToPx() }

        // 底色始终先铺满：图还没到、加载失败、url 为空，看到的都是这块渐变
        CoverPlaceholder(seed = seed, modifier = Modifier.fillMaxSize())

        val bitmap = rememberCover(url, wPx, hPx)
        if (bitmap != null) {
            val alpha = remember { Animatable(0f) }
            LaunchedEffect(bitmap) { alpha.animateTo(1f, tween(200)) }
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { this.alpha = alpha.value },
            )
        }
    }
}

/** 传 Dp 的老写法，内部换算成 Modifier。 */
@Composable
fun CoverBlock(
    seed: String,
    width: Dp,
    height: Dp,
    url: String = "",
) {
    CoverBlock(seed = seed, modifier = Modifier.size(width, height), url = url)
}

/**
 * 渐变占位块。用 seed 的哈希错开起始色相，相邻两个卡片的底色不会一模一样，
 * 列表看起来才不像「加载失败了一整页」。
 */
@Composable
fun CoverPlaceholder(
    seed: String,
    modifier: Modifier = Modifier,
) {
    val hueShift = (seed.hashCode() and 0xFF) / 255f
    val base = CouiTheme.colorScheme.accent
    val start = Color(
        red = base.red * (0.7f + hueShift * 0.3f),
        green = base.green * (0.75f + hueShift * 0.25f),
        blue = base.blue,
        alpha = 0.85f,
    )
    Box(
        modifier.background(Brush.linearGradient(listOf(start, base.copy(alpha = 0.35f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text("封面", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

/**
 * 把 url 变成一张位图。
 *
 * 内存命中时能同步拿到（[CoverLoader.peek]），所以首帧就有图；
 * 未命中才起协程去磁盘/网络拿，拿到后重组。
 */
@Composable
private fun rememberCover(url: String, widthPx: Int, heightPx: Int): ImageBitmap? {
    val ctx = LocalContext.current
    // 尺寸还没测量出来时按 1px 占位，避免用 0 去算降采样倍数
    val w = widthPx.coerceAtLeast(1)
    val h = heightPx.coerceAtLeast(1)

    var bitmap by remember(url, w, h) {
        mutableStateOf(url.takeIf { it.isNotBlank() }?.let { CoverLoader.peek(it, w, h) }?.asImageBitmap())
    }

    LaunchedEffect(url, w, h) {
        if (url.isBlank() || widthPx <= 0 || heightPx <= 0) {
            bitmap = null
            return@LaunchedEffect
        }
        if (bitmap != null) return@LaunchedEffect
        val bmp = CoverLoader.load(ctx, url, w, h)
        bitmap = bmp?.asImageBitmap()
    }

    return bitmap
}
