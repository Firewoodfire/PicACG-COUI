package com.picacg.coui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picacg.coui.data.ApiConfig
import com.picacg.coui.data.ApiConfigStore
import com.picacg.coui.data.HttpRepository
import com.picacg.coui.data.LoginResult
import com.picacg.coui.data.ProbeResult
import com.itos.coui.layout.Divider
import com.itos.coui.switch.Switch
import com.itos.coui.theme.CouiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 设置页：在这里填接口地址就能接上自己的后端，填完点「保存并生效」立即换数据源，不用改代码。
 *
 * 输入时会即时保存到本地（防止误退出丢失），但只有点「保存并生效」才会让各页面重新拉数据。
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAbout: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(ApiConfigStore.config) }
    var probing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ProbeResult?>(null) }
    var hint by remember { mutableStateOf("") }

    // 密码只存在于内存，登录用一次就丢弃，不写进配置也不落盘
    var password by remember { mutableStateOf("") }
    var loggingIn by remember { mutableStateOf(false) }
    var loginResult by remember { mutableStateOf<LoginResult?>(null) }

    val scheme = CouiTheme.colorScheme

    /** 已经生效过的配置，用来判断草稿是否有未生效的改动。 */
    var applied by remember { mutableStateOf(ApiConfigStore.config) }

    /** 改字段时同步落盘，但不在这里触发重载。 */
    fun edit(block: (ApiConfig) -> ApiConfig) {
        val next = block(draft)
        draft = next
        ApiConfigStore.save(next)
        hint = "改动将在 1 秒后自动生效"
    }

    // 输入停下 1 秒后自动生效：不点按钮也能用，边打字时不会每次都发请求
    LaunchedEffect(draft) {
        if (draft == applied) return@LaunchedEffect
        delay(900)
        ApiConfigStore.saveAndApply(draft)
        applied = draft
        hint = if (draft.enabled) "已自动生效，各页面已刷新" else "已自动生效，当前使用示例数据"
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // 顶栏
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onBack() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text("返回", fontSize = 14.sp, color = scheme.accent, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "接口设置",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                // 右上角进「关于」：作者、版本、交流群都在那里
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenAbout() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text("关于", fontSize = 14.sp, color = scheme.accent, fontWeight = FontWeight.Medium)
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                // 当前状态
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (draft.enabled) scheme.accent else scheme.onSurfaceVariant),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    draft.builtin -> "当前使用：内置哔咔接口"
                                    draft.enabled -> "当前使用：自定义接口"
                                    else -> "当前使用：示例数据"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                when {
                                    draft.builtin -> "picaapi.picacomic.com · 签名与路径已内置，登录即可用"
                                    draft.baseUrl.isBlank() -> "填写下方接口地址即可接入你自己的服务"
                                    else -> draft.baseUrl
                                },
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // 接口来源
                SettingsSection("接口来源") {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("使用内置哔咔接口", fontSize = 15.sp, color = scheme.onSurface)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "地址、签名和全部接口路径都已内置，登录后直接用，不用自己填",
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant,
                                lineHeight = 17.sp,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = draft.builtin,
                            onCheckedChange = { v ->
                                edit { it.copy(builtin = v, baseUrl = if (v) "" else it.baseUrl) }
                            },
                            dark = LocalDarkMode.current,
                        )
                    }
                    if (!draft.builtin) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("启用自定义接口", fontSize = 15.sp, color = scheme.onSurface)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "关闭后立即回到示例数据，地址会保留",
                                    fontSize = 12.sp,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(
                                checked = draft.useCustom,
                                onCheckedChange = { v -> edit { it.copy(useCustom = v) } },
                                dark = LocalDarkMode.current,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("兼容模式", fontSize = 15.sp, color = scheme.onSurface)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "关闭背景模糊与 COUI 顶栏/浮动底栏。启动闪退时自动打开",
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant,
                                lineHeight = 17.sp,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = com.picacg.coui.data.ApiConfigStore.compatMode,
                            onCheckedChange = { com.picacg.coui.data.ApiConfigStore.applyCompat(it) },
                            dark = LocalDarkMode.current,
                        )
                    }
                }

                // 账号登录
                SettingsSection("账号登录") {
                    Text(
                        if (draft.builtin) {
                            "用哔咔账号登录。密码只在登录时用一次，不保存；登录成功后令牌会存到本地，之后各页面自动带上。"
                        } else {
                            "填账号密码，点登录自动换取令牌。密码只在登录时用一次，不会保存到本地，也不会写进任何日志。"
                        },
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    CouiField(
                        label = "账号（邮箱 / 用户名）",
                        value = draft.account,
                        placeholder = "you@example.com",
                        keyboardType = KeyboardType.Email,
                        onValueChange = { v -> edit { it.copy(account = v) } },
                    )
                    Spacer(Modifier.height(14.dp))
                    CouiField(
                        label = "密码",
                        value = password,
                        placeholder = "登录密码",
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                        onValueChange = { password = it },
                    )
                    if (!draft.builtin) {
                        Spacer(Modifier.height(14.dp))
                        CouiField(
                            label = "登录接口路径",
                            value = draft.loginPath,
                            placeholder = "/auth/signin",
                            onValueChange = { v -> edit { it.copy(loginPath = v) } },
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CouiButton(
                            text = "登录",
                            primary = true,
                            loading = loggingIn,
                            enabled = draft.enabled,
                            modifier = Modifier.weight(1f),
                        ) {
                            scope.launch {
                                loggingIn = true
                                loginResult = null
                                val r = if (!draft.enabled) {
                                    LoginResult(false, "请先填写接口根地址，或打开内置接口", "", "", "")
                                } else {
                                    runCatching { HttpRepository(draft).login(draft.account, password) }
                                        .getOrElse { LoginResult(false, it.message ?: "未知错误", "", "", "") }
                                }
                                loginResult = r
                                loggingIn = false
                                if (r.ok) {
                                    val next = draft.copy(token = r.token)
                                    draft = next
                                    ApiConfigStore.saveAndApply(next)
                                    applied = next
                                    password = ""
                                    hint = "已登录，令牌已保存并生效"
                                }
                            }
                        }
                        if (draft.token.isNotBlank()) {
                            CouiButton(
                                text = "退出登录",
                                primary = false,
                                modifier = Modifier.weight(1f),
                            ) {
                                val next = draft.copy(token = "")
                                draft = next
                                ApiConfigStore.saveAndApply(next)
                                applied = next
                                loginResult = null
                                hint = "已退出登录，令牌已清除"
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (draft.token.isNotBlank()) scheme.accent else scheme.onSurfaceVariant),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (draft.token.isNotBlank()) {
                                "已登录，令牌：" + draft.token.take(10) + "…" +
                                    "（共 ${draft.token.length} 位）"
                            } else {
                                "未登录"
                            },
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }

                loginResult?.let { r ->
                    Spacer(Modifier.height(12.dp))
                    ProbeCard(ProbeResult(r.ok, r.message, r.url, r.preview))
                }

                // 地址与令牌
                SettingsSection("服务地址") {
                    if (!draft.builtin) {
                        CouiField(
                            label = "接口根地址",
                            value = draft.baseUrl,
                            placeholder = "https://api.example.com",
                            keyboardType = KeyboardType.Uri,
                            onValueChange = { v -> edit { it.copy(baseUrl = v) } },
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    CouiField(
                        label = "访问令牌（登录后自动填入，也可手动填）",
                        value = draft.token,
                        placeholder = "登录后自动填入",
                        onValueChange = { v -> edit { it.copy(token = v) } },
                    )
                    Spacer(Modifier.height(14.dp))
                    if (draft.builtin) {
                        Text(
                            "令牌会写入 Authorization 与 token 两个请求头，内置接口要求原样发送。",
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("令牌原样发送", fontSize = 15.sp, color = scheme.onSurface)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "不加 Bearer 前缀。多数自建接口和 picacg 这类接口需要打开",
                                    fontSize = 12.sp,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(
                                checked = draft.tokenRaw,
                                onCheckedChange = { v -> edit { it.copy(tokenRaw = v) } },
                                dark = LocalDarkMode.current,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "令牌会同时写入 Authorization 与 token 两个请求头。",
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }

                // 路径（内置模式已写死，不显示）
                if (!draft.builtin) {
                SettingsSection("接口路径") {
                    Text(
                        "相对路径会自动拼在根地址后面。占位符：{page} {id} {keyword} {kind}，不用的可以留空。",
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    CouiField(
                        label = "首页信息流",
                        value = draft.homePath,
                        placeholder = "/api/home?page={page}",
                        onValueChange = { v -> edit { it.copy(homePath = v) } },
                    )
                    Spacer(Modifier.height(14.dp))
                    CouiField(
                        label = "分类列表",
                        value = draft.categoriesPath,
                        placeholder = "/api/categories",
                        onValueChange = { v -> edit { it.copy(categoriesPath = v) } },
                    )
                    Spacer(Modifier.height(14.dp))
                    CouiField(
                        label = "排行榜（{kind} = day / week / month）",
                        value = draft.rankingPath,
                        placeholder = "/api/ranking?type={kind}",
                        onValueChange = { v -> edit { it.copy(rankingPath = v) } },
                    )
                    Spacer(Modifier.height(14.dp))
                    CouiField(
                        label = "搜索",
                        value = draft.searchPath,
                        placeholder = "/api/search?q={keyword}",
                        onValueChange = { v -> edit { it.copy(searchPath = v) } },
                    )
                    Spacer(Modifier.height(14.dp))
                    CouiField(
                        label = "详情",
                        value = draft.detailPath,
                        placeholder = "/api/detail/{id}",
                        onValueChange = { v -> edit { it.copy(detailPath = v) } },
                    )
                    Spacer(Modifier.height(14.dp))
                    CouiField(
                        label = "公告列表",
                        value = draft.announcementsPath,
                        placeholder = "/api/announcements",
                        onValueChange = { v -> edit { it.copy(announcementsPath = v) } },
                    )
                }
                }

                // 屏蔽设定
                SettingsSection("屏蔽设定") {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("启用屏蔽规则", fontSize = 15.sp, color = scheme.onSurface)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                if (draft.block.active) {
                                    "已启用 ${draft.block.count} 条，列表与详情都会过滤"
                                } else {
                                    "关闭时所有内容原样显示"
                                },
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = draft.block.enabled,
                            onCheckedChange = { v -> edit { it.copy(block = it.block.copy(enabled = v)) } },
                            dark = LocalDarkMode.current,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "每行一条，格式：\n" +
                            "普通关键词     命中标题 / 简介 / 作者 / 标签任意一处即屏蔽\n" +
                            "author:某某     只看某个维度（author / tag / title / desc）\n" +
                            "re:(?i)a|b      正则，以 re: 开头\n" +
                            "#123456        按条目 id 精确屏蔽\n" +
                            "以 // 开头的行是注释，空行忽略。",
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    CouiField(
                        label = "规则（每行一条）",
                        value = draft.block.raw,
                        placeholder = "女装\nauthor:某某画师\n#123456",
                        singleLine = false,
                        minLines = 5,
                        onValueChange = { v -> edit { it.copy(block = it.block.copy(raw = v)) } },
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CouiButton(
                            text = "清空规则",
                            primary = false,
                            modifier = Modifier.weight(1f),
                        ) {
                            edit { it.copy(block = it.block.copy(raw = "")) }
                        }
                        CouiButton(
                            text = "填入示例",
                            primary = false,
                            modifier = Modifier.weight(1f),
                        ) {
                            edit { it.copy(block = it.block.copy(raw = com.picacg.coui.data.BlockRules.SAMPLE)) }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 关于：作者、版本、QQ 交流群
                Panel(modifier = Modifier.clickable { onOpenAbout() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "关于此应用",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = scheme.onSurface,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "作者、版本信息、QQ 交流群",
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Text("›", fontSize = 20.sp, color = scheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 操作
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CouiButton(
                        text = "保存并生效",
                        primary = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        ApiConfigStore.saveAndApply(draft)
                        applied = draft
                        hint = when {
                            draft.builtin -> "已生效，正在使用内置哔咔接口"
                            draft.enabled -> "已生效，正在使用自定义接口"
                            else -> "已生效，正在使用示例数据"
                        }
                    }
                    CouiButton(
                        text = "测试连接",
                        primary = false,
                        loading = probing,
                        modifier = Modifier.weight(1f),
                    ) {
                        scope.launch {
                            probing = true
                            result = null
                            result = if (!draft.enabled) {
                                ProbeResult(false, "请先打开内置接口或填写接口根地址", "", "")
                            } else {
                                runCatching { HttpRepository(draft).probe() }
                                    .getOrElse { ProbeResult(false, it.message ?: "未知错误", "", "") }
                            }
                            probing = false
                        }
                    }
                }

                if (hint.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(hint, fontSize = 12.sp, color = scheme.accent, modifier = Modifier.padding(start = 4.dp))
                }

                // 测试结果
                result?.let { r ->
                    Spacer(Modifier.height(16.dp))
                    ProbeCard(r)
                }

                // 字段映射说明
                Spacer(Modifier.height(22.dp))
                Panel {
                    Text("字段怎么对应", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "解析是自动的，会依次尝试常见字段名：\n" +
                            "标题 title / name，封面 cover / image / thumb，\n" +
                            "简介 description / desc / summary / author，\n" +
                            "列表 list / docs / items / data / result。\n" +
                            "所以大部分 REST 接口填上地址就能用。解析不到时点「测试连接」，看原始返回再对一下字段。",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/** 一组设置项，外面套 Panel。 */
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = CouiTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
        )
        Panel {
            Column(Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

/** 圆角输入框。COUI 没有通用输入控件，这里按它的材质规范自绘一个。 */
@Composable
fun CouiField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    modifier: Modifier = Modifier,
) {
    val scheme = CouiTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Column(modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = TextStyle(fontSize = 14.sp, color = scheme.onSurface, lineHeight = 20.sp),
            cursorBrush = SolidColor(scheme.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword) PasswordVisualTransformation()
                else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(scheme.container.copy(alpha = 0.55f))
                .border(
                    width = 1.dp,
                    color = if (focused) scheme.accent.copy(alpha = 0.75f) else Color.Transparent,
                    shape = shape,
                )
                .padding(horizontal = 12.dp, vertical = 11.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            placeholder,
                            fontSize = 13.sp,
                            color = scheme.onSurfaceVariant.copy(alpha = 0.65f),
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/** 通用按钮，主按钮用强调色填充。 */
@Composable
fun CouiButton(
    text: String,
    primary: Boolean = true,
    enabled: Boolean = true,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = CouiTheme.colorScheme
    val bg = if (primary) scheme.accent else scheme.container.copy(alpha = 0.7f)
    val fg = if (primary) scheme.onAccent else scheme.onSurface
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .clickable(enabled = enabled && !loading) { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), color = fg, strokeWidth = 2.dp)
        } else {
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg)
        }
    }
}

/** 测试连接的结果卡片。 */
@Composable
private fun ProbeCard(r: ProbeResult) {
    val scheme = CouiTheme.colorScheme
    val okColor = if (r.ok) scheme.accent else Color(0xFFE5484D)
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(okColor),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                if (r.ok) "连接正常" else "连接失败",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = okColor,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(r.message, fontSize = 12.sp, color = scheme.onSurface, lineHeight = 17.sp)
        if (r.url.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("请求地址：${r.url}", fontSize = 11.sp, color = scheme.onSurfaceVariant)
        }
        if (r.preview.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Divider(startIndent = 0.dp, endIndent = 0.dp)
            Spacer(Modifier.height(10.dp))
            Text("原始返回（前 1500 字）", fontSize = 11.sp, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.container.copy(alpha = 0.5f))
                    .padding(10.dp),
            ) {
                Text(
                    r.preview,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}
