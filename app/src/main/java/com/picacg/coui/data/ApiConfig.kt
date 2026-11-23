package com.picacg.coui.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 接口配置。全部字段都在 App 的设置页里可改，改完点「保存并生效」即生效，不需要改代码重新编译。
 *
 * [baseUrl] 为空表示不启用自定义接口，App 会用内置的示例数据。
 */
data class ApiConfig(
    /** 是否启用自定义接口。关闭后即使填了地址也用示例数据。 */
    val useCustom: Boolean = true,
    /** 接口根地址，例如 https://api.example.com 。留空则使用示例数据。 */
    val baseUrl: String = "",
    /** 访问令牌，可留空。登录成功后会自动填入这里。 */
    val token: String = "",
    /** Authorization 头是否原样发送 token（不加 Bearer 前缀）。大多数自建接口需要勾上。 */
    val tokenRaw: Boolean = false,
    /** 登录接口路径。账号密码会 POST 到这里换取 token。 */
    val loginPath: String = "/auth/signin",
    /** 上次登录用的账号，仅用于回填输入框，不保存密码。 */
    val account: String = "",
    /** 首页信息流。{page} 会替换为页码。 */
    val homePath: String = "/api/home?page={page}",
    /** 分类列表。 */
    val categoriesPath: String = "/api/categories",
    /** 排行榜。{kind} 会替换为 day / week / month。 */
    val rankingPath: String = "/api/ranking?type={kind}",
    /** 搜索。{keyword} 会替换为关键词。 */
    val searchPath: String = "/api/search?q={keyword}",
    /** 详情。{id} 会替换为条目 id。 */
    val detailPath: String = "/api/detail/{id}",
    /** 公告列表。 */
    val announcementsPath: String = "/api/announcements",
    /** 屏蔽规则，见 [BlockRules]。 */
    val block: BlockRules = BlockRules(),
) {
    /** 开关打开且填了根地址，才算真正接入接口。 */
    val enabled: Boolean get() = useCustom && baseUrl.isNotBlank()

    /** 拼出完整 URL，相对路径会自动接在 baseUrl 后面。 */
    fun absoluteUrl(path: String): String {
        val p = path.trim()
        if (p.startsWith("http://") || p.startsWith("https://")) return p
        val base = baseUrl.trim().trimEnd('/')
        if (p.isEmpty()) return base
        return base + "/" + p.trimStart('/')
    }

    companion object {
        val DEFAULT = ApiConfig()
    }
}

/**
 * 全局配置仓库。用 SharedPreferences 持久化，同时暴露成 Compose 状态，界面能直接观察。
 *
 * 需要在 Application/Activity 启动时调用一次 [init]。
 */
object ApiConfigStore {

    private const val PREF_NAME = "coui_api_config"

    private var prefs: SharedPreferences? = null

    /** 当前配置。界面可直接读取，值变化会自动触发重组。 */
    var config by mutableStateOf(ApiConfig())
        private set

    /**
     * 配置版本号。每次点「保存并生效」自增，数据页用它作为 LaunchedEffect 的 key，
     * 从而自动重新拉取数据。
     */
    var version by mutableIntStateOf(0)
        private set

    /**
     * 当前的深浅色选择。以前是界面里的一个 remember，退出再进来就回浅色；
     * 放在这里跟接口配置一起落盘，重启后保持上一次的选择。
     */
    var darkMode by mutableStateOf(false)
        private set

    /** 切换深浅色并立即落盘。 */
    fun applyDark(value: Boolean) {
        darkMode = value
        prefs?.edit()?.putBoolean(KEY_DARK, value)?.apply()
    }

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs = sp
        darkMode = sp.getBoolean(KEY_DARK, false)
        config = ApiConfig(
            useCustom = sp.getBoolean(KEY_USE_CUSTOM, true),
            baseUrl = sp.getString(KEY_BASE, "") ?: "",
            token = sp.getString(KEY_TOKEN, "") ?: "",
            tokenRaw = sp.getBoolean(KEY_TOKEN_RAW, false),
            loginPath = sp.getString(KEY_LOGIN, ApiConfig.DEFAULT.loginPath) ?: ApiConfig.DEFAULT.loginPath,
            account = sp.getString(KEY_ACCOUNT, "") ?: "",
            homePath = sp.getString(KEY_HOME, ApiConfig.DEFAULT.homePath) ?: ApiConfig.DEFAULT.homePath,
            categoriesPath = sp.getString(KEY_CATS, ApiConfig.DEFAULT.categoriesPath) ?: ApiConfig.DEFAULT.categoriesPath,
            rankingPath = sp.getString(KEY_RANK, ApiConfig.DEFAULT.rankingPath) ?: ApiConfig.DEFAULT.rankingPath,
            searchPath = sp.getString(KEY_SEARCH, ApiConfig.DEFAULT.searchPath) ?: ApiConfig.DEFAULT.searchPath,
            detailPath = sp.getString(KEY_DETAIL, ApiConfig.DEFAULT.detailPath) ?: ApiConfig.DEFAULT.detailPath,
            announcementsPath = sp.getString(KEY_NOTICE, ApiConfig.DEFAULT.announcementsPath) ?: ApiConfig.DEFAULT.announcementsPath,
            block = BlockRules(
                enabled = sp.getBoolean(KEY_BLOCK_ON, false),
                raw = sp.getString(KEY_BLOCK_RAW, BlockRules.SAMPLE) ?: BlockRules.SAMPLE,
            ),
        )
    }

    /** 写入配置并落盘。不会触发数据重载，重载由 [applyNow] 负责。 */
    fun save(next: ApiConfig) {
        config = next
        prefs?.edit()
            ?.putBoolean(KEY_USE_CUSTOM, next.useCustom)
            ?.putString(KEY_BASE, next.baseUrl.trim())
            ?.putString(KEY_TOKEN, next.token.trim())
            ?.putBoolean(KEY_TOKEN_RAW, next.tokenRaw)
            ?.putString(KEY_LOGIN, next.loginPath)
            ?.putString(KEY_ACCOUNT, next.account.trim())
            ?.putString(KEY_HOME, next.homePath)
            ?.putString(KEY_CATS, next.categoriesPath)
            ?.putString(KEY_RANK, next.rankingPath)
            ?.putString(KEY_SEARCH, next.searchPath)
            ?.putString(KEY_DETAIL, next.detailPath)
            ?.putString(KEY_NOTICE, next.announcementsPath)
            ?.putBoolean(KEY_BLOCK_ON, next.block.enabled)
            ?.putString(KEY_BLOCK_RAW, next.block.raw)
            ?.apply()
    }

    /** 让所有数据页重新加载。 */
    fun applyNow() {
        version++
    }

    /** 保存并立即生效。 */
    fun saveAndApply(next: ApiConfig) {
        save(next)
        applyNow()
    }

    fun reset() {
        config = ApiConfig()
        prefs?.edit()?.clear()?.apply()
    }

    private const val KEY_USE_CUSTOM = "use_custom"
    private const val KEY_BASE = "base_url"
    private const val KEY_TOKEN = "token"
    private const val KEY_TOKEN_RAW = "token_raw"
    private const val KEY_LOGIN = "path_login"
    private const val KEY_ACCOUNT = "account"
    private const val KEY_HOME = "path_home"
    private const val KEY_CATS = "path_categories"
    private const val KEY_RANK = "path_ranking"
    private const val KEY_SEARCH = "path_search"
    private const val KEY_DETAIL = "path_detail"
    private const val KEY_NOTICE = "path_announcements"
    private const val KEY_BLOCK_ON = "block_enabled"
    private const val KEY_BLOCK_RAW = "block_raw"
    private const val KEY_DARK = "dark_mode"
}
