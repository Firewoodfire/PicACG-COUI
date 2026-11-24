package com.picacg.coui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itos.coui.switch.Switch
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.CouiDialogActionStyle
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.dialog.Dialog
import com.itos.coui.theme.CouiTheme
import com.picacg.coui.data.ApiConfigStore
import com.picacg.coui.data.ReleaseInfo
import com.picacg.coui.data.StorageManager
import com.picacg.coui.data.StorageSnapshot
import com.picacg.coui.data.UpdateChecker
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 关于页所有二级入口。 */
enum class AboutPage {
    Storage,
    VersionInfo,
    Legal,
    Certification,
    Status,
    Help,
    ModelNotice,
    Experience,
    SoftwareVersion,
    HardwareVersion,
}

internal data class AboutAction(
    val label: String,
    val value: String = "",
    val page: AboutPage,
)

@Composable
fun AboutDetailScreen(page: AboutPage, onBack: () -> Unit) {
    when (page) {
        AboutPage.Storage -> StoragePage(onBack)
        AboutPage.VersionInfo -> VersionInfoPage(onBack)
        AboutPage.Legal -> LegalPage(onBack)
        AboutPage.Certification -> CertificationPage(onBack)
        AboutPage.Status -> StatusPage(onBack)
        AboutPage.Help -> HelpPage(onBack)
        AboutPage.ModelNotice -> ModelNoticePage(onBack)
        AboutPage.Experience -> ExperiencePage(onBack)
        AboutPage.SoftwareVersion -> SoftwareVersionPage(onBack)
        AboutPage.HardwareVersion -> HardwareVersionPage(onBack)
    }
}

@Composable
internal fun AboutBackButton(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CouiTheme.colorScheme.container.copy(alpha = 0.7f))
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = BackArrowIcon,
                contentDescription = "返回",
                tint = CouiTheme.colorScheme.onSurface,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
private fun AboutSubpage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        AboutBackButton(onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                title,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                color = CouiTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 13.dp, bottom = 20.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
internal fun AboutActionCard(rows: List<AboutAction>, onOpen: (AboutPage) -> Unit) {
    Panel {
        rows.forEachIndexed { index, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpen(row.page) }
                    .padding(vertical = 11.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(row.label, fontSize = 15.sp, color = CouiTheme.colorScheme.onSurface, maxLines = 1)
                }
                if (row.value.isNotBlank()) {
                    Text(
                        row.value,
                        fontSize = 13.sp,
                        color = CouiTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("›", fontSize = 24.sp, color = CouiTheme.colorScheme.onSurfaceVariant)
            }
            if (index != rows.lastIndex) AboutDivider()
        }
    }
}

@Composable
private fun AboutDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(CouiTheme.colorScheme.onSurface.copy(alpha = 0.07f)))
}

@Composable
private fun KeyValueCard(rows: List<Pair<String, String>>) {
    Panel {
        rows.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(row.first, fontSize = 14.sp, color = CouiTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(
                    row.second,
                    fontSize = 13.sp,
                    color = CouiTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index != rows.lastIndex) AboutDivider()
        }
    }
}

@Composable
private fun TextPanel(title: String, body: String) {
    Panel {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CouiTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(body, fontSize = 13.sp, lineHeight = 21.sp, color = CouiTheme.colorScheme.onSurfaceVariant)
    }
}

// ------------------------------------------------------------------ 储存空间

private enum class StorageClearAction(val title: String, val message: String) {
    Covers("清理封面缓存", "只会删除已经加载的封面图片，之后浏览时会重新下载。账号、接口和屏蔽规则不受影响。"),
    OtherCache("清理其他缓存", "将删除应用私有缓存目录中的临时文件，不会删除下载内容或个人设置。"),
    Downloads("清空下载内容", "下载目录中的全部文件都会被永久删除，此操作无法撤销。"),
}

@Composable
private fun StoragePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var snapshot by remember { mutableStateOf<StorageSnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<StorageClearAction?>(null) }
    var notice by remember { mutableStateOf("") }

    LaunchedEffect(revision) {
        busy = true
        snapshot = StorageManager.snapshot(context)
        busy = false
    }

    fun clear(action: StorageClearAction) {
        scope.launch {
            busy = true
            val ok = when (action) {
                StorageClearAction.Covers -> StorageManager.clearCoverCache(context)
                StorageClearAction.OtherCache -> StorageManager.clearOtherCache(context)
                StorageClearAction.Downloads -> StorageManager.clearDownloads(context)
            }
            notice = if (ok) "清理完成" else "部分文件未能删除，请重试"
            revision++
        }
    }

    AboutSubpage("储存空间", onBack) {
        val data = snapshot
        if (data == null) {
            LoadingBlock("正在计算文件大小…")
        } else {
            KeyValueCard(
                listOf(
                    "封面缓存" to "${StorageManager.formatSize(data.coverCache.bytes)} · ${data.coverCache.files} 个文件",
                    "其他缓存" to "${StorageManager.formatSize(data.otherCache.bytes)} · ${data.otherCache.files} 个文件",
                    "下载内容" to "${StorageManager.formatSize(data.downloads.bytes)} · ${data.downloads.files} 个文件",
                    "合计占用" to StorageManager.formatSize(data.totalBytes),
                ),
            )
            Spacer(Modifier.height(12.dp))
            Panel {
                Text("缓存管理", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CouiTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(
                    "缓存用于加快封面加载，清理后不会退出登录。所有操作仅针对本应用的私有目录。",
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = CouiTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CouiButton(
                        "清理封面",
                        primary = false,
                        enabled = data.coverCache.bytes > 0 && !busy,
                        modifier = Modifier.weight(1f),
                    ) { confirm = StorageClearAction.Covers }
                    CouiButton(
                        "清理其他",
                        primary = false,
                        enabled = data.otherCache.bytes > 0 && !busy,
                        modifier = Modifier.weight(1f),
                    ) { confirm = StorageClearAction.OtherCache }
                }
            }
            Spacer(Modifier.height(12.dp))
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("下载内容", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CouiTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (data.downloadFiles.isEmpty()) "暂无文件；当前版本尚未提供漫画下载器"
                            else "共 ${data.downloadFiles.size} 个文件，可逐项删除",
                            fontSize = 12.sp,
                            color = CouiTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (data.downloadFiles.isNotEmpty()) {
                        Text(
                            "全部清空",
                            fontSize = 13.sp,
                            color = CouiTheme.colorScheme.accent,
                            modifier = Modifier.clickable(enabled = !busy) { confirm = StorageClearAction.Downloads }.padding(8.dp),
                        )
                    }
                }
                if (data.downloadFiles.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    data.downloadFiles.forEach { file ->
                        AboutDivider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(file.name, fontSize = 13.sp, color = CouiTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "${StorageManager.formatSize(file.bytes)} · ${formatDate(file.modified)}",
                                    fontSize = 11.sp,
                                    color = CouiTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "删除",
                                fontSize = 13.sp,
                                color = CouiTheme.colorScheme.accent,
                                modifier = Modifier.clickable(enabled = !busy) {
                                    scope.launch {
                                        busy = true
                                        notice = if (StorageManager.deleteDownload(context, file.path)) "已删除 ${file.name}" else "删除失败"
                                        revision++
                                    }
                                }.padding(8.dp),
                            )
                        }
                    }
                }
            }
            if (notice.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(notice, fontSize = 12.sp, color = CouiTheme.colorScheme.accent)
            }
            Spacer(Modifier.height(12.dp))
            CouiButton("重新计算", primary = false, enabled = !busy, modifier = Modifier.fillMaxWidth()) { revision++ }
        }
    }

    confirm?.let { action ->
        Dialog(
            onDismissRequest = { confirm = null },
            confirmAction = CouiDialogAction(
                text = "确认清理",
                onClick = { confirm = null; clear(action) },
                style = CouiDialogActionStyle.Primary,
                colors = CouiDialogDefaults.primaryActionColors(CouiTheme.colorScheme.accent),
            ),
            dismissAction = CouiDialogAction(text = "取消", onClick = { confirm = null }),
            title = action.title,
            message = action.message,
            placement = CouiDialogPlacement.Center,
        )
    }
}

private fun formatDate(time: Long): String =
    if (time <= 0) "时间未知" else SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(time))

// ------------------------------------------------------------------ 补充信息页

@Composable
private fun ModelNoticePage(onBack: () -> Unit) {
    AboutSubpage("大模型明示告知", onBack) {
        TextPanel("辅助开发说明", "本项目的部分界面文案、实现代码和故障排查过程由大模型辅助生成，再由作者整理、修改和验证。最终交付内容由项目作者负责，不代表任何设备厂商或第三方内容平台的官方立场。")
        Spacer(Modifier.height(12.dp))
        TextPanel("运行时行为", "应用运行时不会调用大模型接口，也不会把你的搜索词、屏蔽规则、账号、令牌、文件或浏览记录发送给大模型服务。网络请求只发往你在设置中配置的数据源，以及你在软件版本页主动触发的 GitHub Releases 查询。")
        Spacer(Modifier.height(12.dp))
        TextPanel("内容责任", "通过自定义接口展示的漫画、封面、公告等内容的真实性与合法性由数据源及相关权利人负责。使用大模型生成或处理内容时，请自行遵守所在地区法律与平台规则。")
    }
}

@Composable
private fun HardwareVersionPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val density = context.resources.configuration.densityDpi
    val abis = Build.SUPPORTED_ABIS.joinToString("、")
    AboutSubpage("硬件版本", onBack) {
        KeyValueCard(
            listOf(
                "品牌" to Build.BRAND,
                "制造商" to Build.MANUFACTURER,
                "设备型号" to Build.MODEL,
                "设备代号" to Build.DEVICE,
                "产品名" to Build.PRODUCT,
                "主板" to Build.BOARD,
                "硬件标识" to Build.HARDWARE,
                "屏幕密度" to "${density} dpi",
                "支持架构" to abis,
            ),
        )
        Spacer(Modifier.height(12.dp))
        TextPanel("说明", "以上读取的是当前设备的公开系统属性，用来方便反馈兼容性问题。应用不会上传这些信息；反馈时是否提供由你自行决定。")
    }
}

// ------------------------------------------------------------------ 信息页

@Composable
private fun VersionInfoPage(onBack: () -> Unit) {
    AboutSubpage("版本信息", onBack) {
        KeyValueCard(
            listOf(
                "应用名称" to "PicACG COUI",
                "版本名称" to BuildConfig.VERSION_NAME,
                "版本号" to BuildConfig.VERSION_CODE.toString(),
                "应用包名" to BuildConfig.APPLICATION_ID,
                "最低系统" to "Android 12（API 31）",
                "界面框架" to "Jetpack Compose · COUI-Kit",
                "样式参考" to "ColorOS 17",
            ),
        )
        Spacer(Modifier.height(12.dp))
        TextPanel("构建说明", "这是一个独立构建的 Android 应用，不是对原版哔咔安装包的代码级修改。当前交付为直接安装版本，未经过应用商店审核。")
    }
}

@Composable
private fun LegalPage(onBack: () -> Unit) {
    AboutSubpage("法律信息", onBack) {
        TextPanel("内容与版权", "应用本身不内置漫画正文，也不授予任何第三方内容的版权。漫画、封面与公告来自用户自行配置的数据源，相关权利归原权利人所有。请仅访问你有权使用的内容，并遵守数据源服务条款及当地法律。")
        Spacer(Modifier.height(12.dp))
        TextPanel("独立项目声明", "PicACG COUI 是独立的界面与接口适配项目，与哔咔官方、OPPO、ColorOS 或 COUI-Kit 作者不存在隶属、赞助或官方合作关系。名称与样式说明仅用于标识兼容性和设计参考。")
        Spacer(Modifier.height(12.dp))
        TextPanel("隐私与账号", "接口地址、令牌、屏蔽规则和主题选择仅保存在本机应用私有存储中。登录密码只用于当次登录请求，不写入配置。清理缓存不会删除这些设置；卸载应用会由系统清除其私有数据。")
        Spacer(Modifier.height(12.dp))
        TextPanel("第三方组件", "项目使用 Jetpack Compose、AndroidX 与 COUI-Kit 等开源组件。各组件仍适用其原有许可证；分发源码时应保留相应版权和许可证说明。")
    }
}

@Composable
private fun CertificationPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val digest = remember(context) { signingCertificateSha256(context) }
    AboutSubpage("认证信息", onBack) {
        KeyValueCard(
            listOf(
                "包名" to BuildConfig.APPLICATION_ID,
                "签名方案" to "APK Signature Scheme v2 / v3",
                "签名证书 SHA-256" to (digest ?: "无法读取"),
                "应用商店认证" to "未提交",
                "企业或 CA 认证" to "无",
            ),
        )
        Spacer(Modifier.height(12.dp))
        TextPanel("如何核对", "这里显示的是当前安装包实际签名证书的 SHA-256 摘要，而不是伪造的认证标识。更新安装前可对比新旧版本；如果摘要不一致，Android 通常会拒绝覆盖安装，请先备份并确认来源。")
    }
}

@Composable
private fun StatusPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var storage by remember { mutableStateOf<StorageSnapshot?>(null) }
    LaunchedEffect(revision) { storage = StorageManager.snapshot(context) }
    val config = ApiConfigStore.config
    val network = remember(revision) { networkStatus(context) }
    AboutSubpage("状态信息", onBack) {
        KeyValueCard(
            listOf(
                "网络连接" to network,
                "数据源" to when {
                    config.builtin -> "内置哔咔接口"
                    config.enabled -> "自定义接口"
                    else -> "内置示例数据"
                },
                "接口地址" to config.effectiveBase.ifBlank { "未配置" },
                "登录令牌" to if (config.token.isBlank()) "未配置" else "已配置（内容已隐藏）",
                "内容屏蔽" to if (config.block.active) "已启用" else "未启用",
                "主题模式" to if (ApiConfigStore.darkMode) "深色" else "浅色",
                "兼容模式" to if (ApiConfigStore.compatMode) "已开启" else "已关闭",
                "本地占用" to (storage?.let { StorageManager.formatSize(it.totalBytes) } ?: "计算中…"),
            ),
        )
        Spacer(Modifier.height(12.dp))
        CouiButton("刷新状态", primary = false, modifier = Modifier.fillMaxWidth()) { revision++ }

        val crash = remember(revision) { CrashGuard.read(context) }
        if (crash.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Panel {
                Text(
                    "上次启动崩溃日志",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CouiTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "已自动切到兼容模式，避免再次闪退。把这段日志反馈给开发者即可定位问题。",
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = CouiTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    crash.take(3000),
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = CouiTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CouiButton("复制日志", primary = false, modifier = Modifier.weight(1f)) {
                        runCatching {
                            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                            cm?.setPrimaryClip(
                                android.content.ClipData.newPlainText("PicACG crash", crash),
                            )
                        }
                    }
                    CouiButton("清除记录", primary = false, modifier = Modifier.weight(1f)) {
                        CrashGuard.clear(context)
                        revision++
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpPage(onBack: () -> Unit) {
    AboutSubpage("使用说明", onBack) {
        TextPanel("1. 配置数据源", "点主页右下角设置，填写可返回 JSON 的接口根地址和对应路径。路径支持 {page}、{category}、{keyword}、{kind}、{id} 占位符；点“测试连接”可查看原始响应。没有可用接口时可关闭自定义数据源，浏览内置示例。")
        Spacer(Modifier.height(12.dp))
        TextPanel("2. 登录与浏览", "在设置中填写账号、密码和登录路径，登录成功后自动保存令牌。主页支持关键词搜索、分类筛选和自动分页；底栏可切换分类、排行与公告。封面加载失败时会保留渐变占位。")
        Spacer(Modifier.height(12.dp))
        TextPanel("3. 屏蔽与主题", "设置中的屏蔽规则支持普通关键词、author:/tag:/title:/desc: 维度、re: 正则和 #ID 精确匹配。主页左下角悬浮按钮切换深浅色，选择会保存在本机。")
        Spacer(Modifier.height(12.dp))
        TextPanel("4. 储存与更新", "关于此应用 → 储存空间可查看并清理真实缓存，以及管理下载目录中已有文件。软件版本页可查看更新历史；关联 GitHub 仓库后，可检查 Releases 中的新版本。QQ 交流群：1121329450。")
    }
}

// ------------------------------------------------------------------ 体验改进计划

@Composable
private fun ExperiencePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("experience_plan", Context.MODE_PRIVATE) }
    var joined by remember { mutableStateOf(prefs.getBoolean("joined", false)) }
    AboutSubpage("体验改进计划", onBack) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("加入体验改进计划", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CouiTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(if (joined) "已记录参与意愿" else "未加入", fontSize = 12.sp, color = CouiTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = joined,
                    onCheckedChange = {
                        joined = it
                        prefs.edit().putBoolean("joined", it).apply()
                    },
                    dark = LocalDarkMode.current,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TextPanel("计划说明", "此计划用于收集未来版本的产品反馈与改进建议。当前版本没有接入统计、崩溃上报或遥测服务，因此开关只在本机保存参与意愿，不会自动上传使用记录、账号、令牌、设备标识或文件。")
        Spacer(Modifier.height(12.dp))
        TextPanel("如何参与", "目前可通过 QQ 交流群 1121329450 提交问题与建议。反馈问题时请描述 Android 版本、应用版本和复现步骤；不要发送密码或完整访问令牌。若以后加入诊断数据上报，会先在应用内说明范围并再次征得同意。")
        Spacer(Modifier.height(12.dp))
        TextPanel("随时退出", "关闭上方开关即可退出。退出只改变本机的参与意愿，不影响浏览、登录、缓存和更新检查等任何功能。")
    }
}

// ------------------------------------------------------------------ 软件版本与更新

@Composable
private fun SoftwareVersionPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }

    AboutSubpage("软件版本", onBack) {
        KeyValueCard(
            listOf(
                "当前版本" to "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                "发布通道" to "GitHub Releases",
                "项目仓库" to (UpdateChecker.projectUrl.ifBlank { "尚未关联" }),
            ),
        )
        Spacer(Modifier.height(12.dp))
        Panel {
            Text("检查更新", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CouiTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "仅请求公开仓库的最新 Release，不会上传设备、账号或使用数据。",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = CouiTheme.colorScheme.onSurfaceVariant,
            )
            if (checking) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = CouiTheme.colorScheme.accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("正在查询 GitHub Releases…", fontSize = 12.sp, color = CouiTheme.colorScheme.onSurfaceVariant)
                }
            }
            release?.let { info ->
                Spacer(Modifier.height(14.dp))
                Text(
                    if (info.hasUpdate) "发现新版本 ${info.version}" else "当前已是最新版本（${info.version}）",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = CouiTheme.colorScheme.accent,
                )
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(info.notes.take(1200), fontSize = 12.sp, lineHeight = 18.sp, color = CouiTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (message.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(message, fontSize = 12.sp, lineHeight = 18.sp, color = CouiTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            CouiButton("检查更新", enabled = !checking, modifier = Modifier.fillMaxWidth()) {
                scope.launch {
                    checking = true
                    message = ""
                    release = null
                    UpdateChecker.checkLatest()
                        .onSuccess {
                            release = it
                            message = if (infoDate(it).isBlank()) "" else "发布时间：${infoDate(it)}"
                        }
                        .onFailure { message = it.message ?: "检查更新失败" }
                    checking = false
                }
            }
            val url = release?.url?.ifBlank { null } ?: UpdateChecker.releasesUrl
            if (url.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                CouiButton("查看更新页面", primary = false, modifier = Modifier.fillMaxWidth()) { openWebPage(context, url) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "关于这个应用",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CouiTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "完整日志",
                    fontSize = 12.sp,
                    color = CouiTheme.colorScheme.accent,
                    modifier = Modifier.clickable {
                        openWebPage(context, "${UpdateChecker.projectUrl}/blob/main/CHANGELOG.md")
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "PicACG COUI 是一个 COUI / ColorOS 17 风格的漫画浏览客户端：" +
                    "主页信息流、关键词搜索、分类筛选、日/周/月排行、公告、详情与章节目录，" +
                    "都在同一套毛玻璃界面里。接口默认走哔咔官方 picaapi，登录后即可使用。",
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = CouiTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun infoDate(info: ReleaseInfo): String = info.publishedAt

private fun openWebPage(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun networkStatus(context: Context): String {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return "无法读取"
    val network = manager.activeNetwork ?: return "未连接"
    val caps = manager.getNetworkCapabilities(network) ?: return "已连接"
    val type = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动网络"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
        else -> "其他网络"
    }
    val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    return "$type · ${if (validated) "互联网可用" else "等待验证"}"
}

@Suppress("DEPRECATION")
private fun signingCertificateSha256(context: Context): String? = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    val signatures = info.signingInfo?.apkContentsSigners ?: return null
    val digest = MessageDigest.getInstance("SHA-256").digest(signatures.first().toByteArray())
    digest.joinToString(":") { "%02X".format(it) }
}.getOrNull()
