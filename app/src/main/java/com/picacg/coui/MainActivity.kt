package com.picacg.coui

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.itos.coui.backdrop.ProvideCouiBackdrop
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.drawCouiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.itos.coui.effect.couiShadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import com.itos.coui.layout.Scaffold
import com.itos.coui.navigation.CouiNavigationItem
import com.itos.coui.navigation.NavigationBar
import com.itos.coui.theme.CouiTheme
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 读取本地保存的接口配置，必须在 setContent 之前
        com.picacg.coui.data.ApiConfigStore.init(applicationContext)
        setContent { App() }
    }
}

/**
 * 底部主导航项，对应哔咔的栏目结构。
 *
 * 原来有 5 项，其中「组件」是 COUI 组件图鉴，不是漫画功能，已移除；
 * 剩下的 4 项每个字都能完整显示，不会再出现「排...」「组...」这种截断。
 */
private enum class Tab(val label: String) {
    Home("主页"),
    Category("分类"),
    Ranking("排行"),
    Notice("公告"),
}

@Composable
private fun App() {
    // 深色模式存在 SharedPreferences 里，重启后仍保持上次的选择
    val dark = com.picacg.coui.data.ApiConfigStore.darkMode

    // 状态栏 / 导航栏的图标颜色要跟着反转，否则深色模式下是深色图标压在深色背景上，
    // 时间、电量完全看不见。窗口是透明的（themes.xml），所以必须手动设。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CouiTheme(darkTheme = dark) {
        var tab by remember { mutableStateOf(Tab.Home) }
        var route by remember { mutableStateOf<Route?>(null) }

        androidx.compose.runtime.CompositionLocalProvider(LocalDarkMode provides dark) {
        // 窗口是透明的（见 res/values/themes.xml），这里自己铺一层实色底，
        // 既保证文字对比度，也让磨砂玻璃有确定的背景可透
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(CouiTheme.colorScheme.background),
        ) {
            when (val current = route) {
                is Route.Detail -> DetailScreen(id = current.id, onBack = { route = null })
                Route.Settings -> SettingsScreen(
                    onBack = { route = null },
                    onOpenAbout = { route = Route.About },
                )

                Route.About -> AboutScreen(
                    onBack = { route = null },
                    onOpen = { route = Route.AboutDetail(it) },
                )
                is Route.AboutDetail -> AboutDetailScreen(current.page, onBack = { route = Route.About })
                null -> BackdropRoot {
                    Scaffold(
                        topBar = {
                            LargeTopAppBar(
                                title = {
                                    // 主页显示 App 名（效果和 COUI 组件库首页那个大标题一致），
                                    // 其余栏目显示自己的名字
                                    Text(
                                        if (tab == Tab.Home) "PicACG" else tab.label,
                                        maxLines = 1,
                                    )
                                },
                                // 40sp / semibold 与 COUI 组件库首页大标题（CouiTextKind.HomeTitle）完全一致
                                expandedTitleStyle = TextStyle(
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.5).sp,
                                ),
                                // 收起后与 COUI 的 CouiTextKind.TopBar 一致：18sp / medium
                                collapsedTitleStyle = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                scrollBehavior = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
                                // 深色/浅色已移到左下角悬浮按钮，顶栏不再放操作项
                            )
                        },
                        bottomBar = {
                            BottomTabs(selected = tab, onSelect = { tab = it })
                        },
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            AmbientGlow()

                            Box(Modifier.fillMaxSize()) {
                                when (tab) {
                                    Tab.Home -> HomeScreen(onOpen = { route = Route.Detail(it) })
                                    Tab.Category -> CategoryScreen(onOpen = { route = Route.Detail(it) })
                                    Tab.Ranking -> RankingScreen(onOpen = { route = Route.Detail(it) })
                                    Tab.Notice -> NoticeScreen()
                                }
                            }

                            // 左：深色 / 浅色切换    右：设置
                            Row(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, bottom = 84.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FloatingCircle(
                                    icon = if (dark) SunIcon else MoonIcon,
                                    contentDescription = if (dark) "切换到浅色" else "切换到深色",
                                    onClick = { com.picacg.coui.data.ApiConfigStore.applyDark(!dark) },
                                )
                                FloatingCircle(
                                    icon = Icons.Filled.Settings,
                                    contentDescription = "设置",
                                    onClick = { route = Route.Settings },
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

/**
 * 悬浮圆形按钮，样式参考 ColorOS 的悬浮操作键：
 * 实心圆 + 投影 + 按下缩放，选中时用强调色填充。
 *
 * 用 Icons.Filled.Settings 与 Notifications 两个图标，
 * 不带文字标签——截图里那种圆形 FAB 也是纯图标。
 */
@Composable
private fun FloatingCircle(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    val scheme = CouiTheme.colorScheme
    val fg = if (selected) scheme.onAccent else scheme.onSurface

    // 选中态用实心强调色；未选中态做成磨砂玻璃圆钮，能透出背后滚动的封面
    val surface = if (selected) {
        Modifier.clip(CircleShape).background(scheme.accent)
    } else {
        Modifier.couiGlass(CircleShape, alpha = 0.40f, blurRadius = 22.dp)
    }

    Box(
        surface
            .size(56.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = fg.copy(alpha = 0.25f)),
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = fg,
            modifier = Modifier.size(24.dp),
        )
    }
}


/** 月亮：当前是浅色主题，点一下进入深色。 */
private val MoonIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Moon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // 一弯新月：大圆挖掉一个偏移的圆
            moveTo(20.5f, 15.2f)
            curveTo(16.1f, 17.4f, 10.9f, 15.0f, 10.9f, 10.1f)
            curveTo(10.9f, 7.1f, 12.6f, 4.9f, 15.0f, 3.8f)
            curveTo(13.6f, 3.4f, 12.1f, 3.3f, 10.6f, 3.7f)
            curveTo(5.6f, 5.1f, 2.7f, 10.3f, 4.1f, 15.3f)
            curveTo(5.5f, 20.3f, 10.7f, 23.2f, 15.7f, 21.8f)
            curveTo(17.9f, 21.2f, 19.6f, 19.9f, 20.8f, 18.2f)
            curveTo(20.9f, 17.2f, 20.8f, 16.2f, 20.5f, 15.2f)
            close()
        }
    }.build()
}

/** 太阳：当前是深色主题，点一下回到浅色。 */
private val SunIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Sun",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // 圆心
            moveTo(12f, 8f)
            curveTo(14.2f, 8f, 16f, 9.8f, 16f, 12f)
            curveTo(16f, 14.2f, 14.2f, 16f, 12f, 16f)
            curveTo(9.8f, 16f, 8f, 14.2f, 8f, 12f)
            curveTo(8f, 9.8f, 9.8f, 8f, 12f, 8f)
            close()
            // 八条光芒
            moveTo(11f, 1.5f); lineTo(13f, 1.5f); lineTo(13f, 5f); lineTo(11f, 5f); close()
            moveTo(11f, 19f); lineTo(13f, 19f); lineTo(13f, 22.5f); lineTo(11f, 22.5f); close()
            moveTo(1.5f, 11f); lineTo(5f, 11f); lineTo(5f, 13f); lineTo(1.5f, 13f); close()
            moveTo(19f, 11f); lineTo(22.5f, 11f); lineTo(22.5f, 13f); lineTo(19f, 13f); close()
            moveTo(3.5f, 5.2f); lineTo(5.2f, 3.5f); lineTo(7.7f, 6f); lineTo(6f, 7.7f); close()
            moveTo(16.3f, 18f); lineTo(18f, 16.3f); lineTo(20.5f, 18.8f); lineTo(18.8f, 20.5f); close()
            moveTo(3.5f, 18.8f); lineTo(6f, 16.3f); lineTo(7.7f, 18f); lineTo(5.2f, 20.5f); close()
            moveTo(16.3f, 6f); lineTo(18.8f, 3.5f); lineTo(20.5f, 5.2f); lineTo(18f, 7.7f); close()
        }
    }.build()
}


/** 路由：详情页与设置页。 */
private sealed interface Route {
    data class Detail(val id: String) : Route
    object Settings : Route
    object About : Route
    data class AboutDetail(val page: AboutPage) : Route
}

/** 底部标签栏。API 34+ 用 COUI 原生 NavigationBar（ColorOS 浮动胶囊），低版本降级。 */
@Composable
private fun BottomTabs(selected: Tab, onSelect: (Tab) -> Unit) {
    if (Build.VERSION.SDK_INT >= 34) {
        val items = Tab.entries.map { t ->
            CouiNavigationItem(
                title = t.label,
                icon = iconOf(t),
                selectedIcon = iconOf(t),
            )
        }
        Box(Modifier.fillMaxWidth()) {
            NavigationBar(
                items = items,
                selectedIndex = Tab.entries.indexOf(selected),
                onSelection = { onSelect(Tab.entries[it]) },
            )
        }
        return
    }
    // 低版本降级：图标 + 文字，避免只剩文字时显得空
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(CouiTheme.colorScheme.container.copy(alpha = 0.85f))
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tab.entries.forEach { t ->
                val on = selected == t
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) CouiTheme.colorScheme.accent else Color.Transparent)
                        .clickable { onSelect(t) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = iconOf(t),
                        contentDescription = t.label,
                        tint = if (on) CouiTheme.colorScheme.onAccent
                        else CouiTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        t.label,
                        fontSize = 11.sp,
                        maxLines = 1,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) CouiTheme.colorScheme.onAccent
                        else CouiTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 底栏图标。每个栏目一个不重复的图标。 */
private fun iconOf(t: Tab): ImageVector = when (t) {
    Tab.Home -> Icons.Filled.Home
    Tab.Category -> Icons.AutoMirrored.Filled.List
    Tab.Ranking -> Icons.Filled.Star
    Tab.Notice -> Icons.Filled.Notifications
}

/** 背景光斑，让 COUI 的材质有内容可透出。 */
@Composable
private fun BoxScope.AmbientGlow() {
    val accent = CouiTheme.colorScheme.accent
    val second = CouiTheme.colorScheme.accentOnSurface
    // 三团光斑：玻璃卡片要靠它们透出颜色，只有一团的话卡片看起来还是灰的
    Box(
        Modifier
            .size(340.dp)
            .align(Alignment.TopEnd)
            .background(
                Brush.radialGradient(listOf(accent.copy(alpha = 0.30f), Color.Transparent)),
                CircleShape,
            ),
    )
    Box(
        Modifier
            .size(280.dp)
            .align(Alignment.BottomStart)
            .background(
                Brush.radialGradient(listOf(second.copy(alpha = 0.20f), Color.Transparent)),
                CircleShape,
            ),
    )
    Box(
        Modifier
            .size(220.dp)
            .align(Alignment.Center)
            .offset(x = 40.dp, y = (-60).dp)
            .background(
                Brush.radialGradient(listOf(accent.copy(alpha = 0.14f), Color.Transparent)),
                CircleShape,
            ),
    )
}

/**
 * 当前页面的 backdrop。COUI 库内部自己有一个，但声明成 internal 外部访问不到，
 * 这里自己挂一个，效果等价。
 */
internal val LocalBackdrop = androidx.compose.runtime.compositionLocalOf<CouiBackdrop?> { null }

/**
 * 当前是否深色。COUI 的 SegmentedControl 需要显式告诉它现在是深色还是浅色，
 * 它内部据此挑容器色、文字色和材质；不传的话它自己去问系统，
 * 而本 App 的深浅色是手动切换的，跟系统设置可能不一致。
 */
internal val LocalDarkMode = androidx.compose.runtime.compositionLocalOf { false }

/**
 * 背景模糊用的根容器。
 *
 * COUI 的 `rememberCouiBackdrop` 会把这个容器画出来的内容录进一个 GraphicsLayer，
 * 之后任何调用 [couiGlass] 的子元素都能拿到「自己背后那块区域」再做模糊——
 * 也就是真·背景模糊，不是给半透明色上盖一层灰。
 *
 * 用法上必须包住整页（背景、内容都在里面），否则录不到东西，模糊出来是透明的。
 */
@Composable
private fun BackdropRoot(content: @Composable () -> Unit) {
    if (Build.VERSION.SDK_INT < 31) {
        content()
        return
    }
    // 参数顺序：parent / diagnosticName / drawSource。
    // drawSource 负责把「背景」录进 layer，后面的 drawCouiBackdrop 才有的可透。
    val backdrop = rememberCouiBackdrop(
        parent = null,
        diagnosticName = "root",
        drawSource = { drawContent() },
    )
    Box(
        Modifier
            .fillMaxSize()
            .couiBackdrop(backdrop),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalBackdrop provides backdrop) {
            ProvideCouiBackdrop(backdrop) { content() }
        }
    }
}

/**
 * 玻璃材质：半透明底色 + 背后内容模糊 + 细描边。
 *
 * @param alpha 底色不透明度。数值越小越透，0.45 大约是 iOS 那种能看见底色的程度
 * @param blurRadius 模糊半径，单位 dp。20dp 左右是 ColorOS 控制中心那种「奶白玻璃」
 * @param border 是否描一圈极淡的边，让边界在深色背景上也清楚
 */
@Composable
fun Modifier.couiGlass(
    shape: Shape,
    alpha: Float = 0.45f,
    blurRadius: Dp = 20.dp,
    border: Boolean = true,
): Modifier {
    val scheme = CouiTheme.colorScheme
    var m = this
    val backdrop = LocalBackdrop.current
    if (Build.VERSION.SDK_INT >= 31 && backdrop != null) {
        m = m.drawCouiBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(blurRadius.value) },
        )
    }
    m = m
        .clip(shape)
        .background(scheme.card.copy(alpha = if (Build.VERSION.SDK_INT >= 31) alpha else alpha + 0.35f))
    if (border) {
        m = m.border(1.dp, scheme.onSurface.copy(alpha = 0.06f), shape)
    }
    return m
}

/**
 * 通用卡片容器：API 34+ 用 COUI 轮廓投影，低版本降级系统投影。
 *
 * 内部用 [Column] 而不是 [Box]——这是之前所有文字重叠的根源：
 * Box 的子项会叠在同一个位置，里面再放多个文本就会互相压住。
 *
 * 这一版把材质换成了磨砂玻璃：透出背后的氛围光斑，边缘一圈极淡描边。
 * API 31 以下没有可用的背景模糊，会自动退回不透明度更高的纯色卡片，保证可读。
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val shadowed = if (Build.VERSION.SDK_INT >= 34) {
        modifier.fillMaxWidth().then(Modifier.couiShadowOf())
    } else {
        modifier.fillMaxWidth().shadow(6.dp, shape)
    }
    // 投影层级要比背景录屏层级低，否则投影会被一起裁掉
    Column(
        shadowed
            .couiGlass(shape, alpha = 0.34f, blurRadius = 18.dp)
            .padding(16.dp),
        content = content,
    )
}

/** 把 COUI 的投影包一层，隔离 API 30+ 符号引用。 */
@Composable
private fun Modifier.couiShadowOf(): Modifier = this.couiShadow()

/** 加载中占位。 */
@Composable
fun LoadingBlock(text: String = "加载中…") {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = CouiTheme.colorScheme.accent,
            strokeWidth = 2.5.dp,
        )
        Spacer(Modifier.height(14.dp))
        Text(text, fontSize = 13.sp, color = CouiTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 数据加载失败的提示。以前失败会静默显示空白，现在把原因写出来，
 * 接自己接口时能直接看到是地址错、路径错还是解析不到字段。
 */
@Composable
fun ErrorBlock(message: String, onRetry: (() -> Unit)? = null) {
    val scheme = CouiTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE5484D).copy(alpha = 0.10f))
            .padding(16.dp),
    ) {
        Text("数据加载失败", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE5484D))
        Spacer(Modifier.height(6.dp))
        Text(message, fontSize = 12.sp, color = scheme.onSurfaceVariant, lineHeight = 17.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "去「设置」检查接口地址与路径，可用「测试连接」看原始返回。",
            fontSize = 11.sp,
            color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            CouiButton(text = "重试", primary = false, modifier = Modifier.fillMaxWidth()) { onRetry() }
        }
    }
}

/** 公告页用到的日期徽章。 */
@Composable
fun DateBadge(date: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CouiTheme.colorScheme.accent.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(date, fontSize = 11.sp, color = CouiTheme.colorScheme.accent, maxLines = 1)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        color = CouiTheme.colorScheme.onSurface,
    )
}
