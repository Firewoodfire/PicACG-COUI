package com.picacg.coui.data

import com.picacg.coui.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val version: String,
    val title: String,
    val url: String,
    val publishedAt: String,
    val notes: String,
    val hasUpdate: Boolean,
)

/** GitHub Releases 更新检查；只读取公开 release，不上传任何设备或账号信息。 */
object UpdateChecker {

    val projectUrl: String
        get() = if (BuildConfig.GITHUB_REPOSITORY.isBlank()) ""
        else "https://github.com/${BuildConfig.GITHUB_REPOSITORY}"

    val releasesUrl: String
        get() = if (projectUrl.isBlank()) "" else "$projectUrl/releases"

    suspend fun checkLatest(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        val repo = BuildConfig.GITHUB_REPOSITORY.trim()
        if (repo.isBlank()) {
            return@withContext Result.failure(IllegalStateException("尚未关联 GitHub 仓库，暂时无法在线检查更新"))
        }
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "PicACG-COUI/${BuildConfig.VERSION_NAME}")
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 404) {
                return@withContext Result.failure(IllegalStateException("仓库还没有公开发布版本"))
            }
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException("检查更新失败（HTTP $code）"))
            }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank { json.optString("name") }
            val latest = ReleaseInfo(
                version = tag,
                title = json.optString("name").ifBlank { tag },
                url = json.optString("html_url"),
                publishedAt = json.optString("published_at").take(10),
                notes = json.optString("body"),
                hasUpdate = isNewer(tag, BuildConfig.VERSION_NAME),
            )
            Result.success(latest)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("网络连接失败：${e.message ?: "请稍后重试"}"))
        } finally {
            connection?.disconnect()
        }
    }

    /** 数字段逐段比较；v1.4.0 与 1.4.0 等价，无法解析的后缀不参与大小判断。 */
    internal fun isNewer(candidate: String, current: String): Boolean {
        fun parts(text: String): List<Int> = text.trim().removePrefix("v").removePrefix("V")
            .split(Regex("[._-]"))
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av > bv
        }
        return false
    }
}
