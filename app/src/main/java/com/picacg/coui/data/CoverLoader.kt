package com.picacg.coui.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 封面图加载器。**没有引入 Coil / Glide**，自己写了一个够用的最小实现。
 *
 * 为什么不引图片库：这个 App 只用来显示封面，Coil 完整版会带 OkHttp、协程调度、
 * 动图解码等一堆东西，APK 要多出 2MB 左右。这里唯一的诉求是「把 url 变成一张
 * 不超过格子大小的位图，内存里缓存住，别反复下载」，两百行足够。
 *
 * 三层结构：
 *  1. [mem]   内存 LRU，按字节数计（默认 32MB）。命中时同步返回，列表回滚不闪白。
 *  2. 磁盘缓存 `cacheDir/covers/`，用 url 的 SHA-1 当文件名。冷启动、切页不重复下载。
 *  3. 网络。带 UA 与 Referer，部分图床（含哔咔的 storage 域名）缺这两个会返回 403。
 *
 * 解码按目标尺寸做 inSampleSize 降采样，避免把 2000px 的原图整张读进内存。
 */
object CoverLoader {

    /** 内存缓存上限。32MB 大约能放 80 张 400×560 的封面。 */
    private const val MEM_LIMIT = 32 * 1024 * 1024

    /** 磁盘缓存上限，超过就整体清空（比逐个算 LRU 简单，也够用）。 */
    private const val DISK_LIMIT = 120L * 1024 * 1024

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    private val mem = object : LruCache<String, Bitmap>(MEM_LIMIT) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** 同一张图同时被多个位置请求时，只下载一次。 */
    private val locks = HashMap<String, Any>()

    /**
     * 从内存缓存里取，取不到返回 null。界面用它做首帧，避免「先白一下再出图」。
     * 不是 suspend，可以在 composition 里直接调。
     */
    fun peek(url: String, widthPx: Int, heightPx: Int): Bitmap? {
        if (url.isBlank()) return null
        return mem.get(keyOf(url, widthPx, heightPx))
    }

    /**
     * 加载封面。依次试内存 → 磁盘 → 网络，全部失败返回 null（界面退回渐变占位）。
     *
     * @param widthPx 目标显示宽度，用来算降采样倍数，不需要精确
     */
    suspend fun load(ctx: Context, url: String, widthPx: Int, heightPx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext null
            val absolute = absolute(url, ApiConfigStore.config.baseUrl)
            if (!absolute.startsWith("http://") && !absolute.startsWith("https://")) {
                return@withContext null
            }
            val key = keyOf(absolute, widthPx, heightPx)

            mem.get(key)?.let { return@withContext it }

            val lock = synchronized(locks) { locks.getOrPut(key) { Any() } }
            try {
                synchronized(lock) {
                    // 拿到锁后再查一次：前一个请求可能刚刚填好缓存
                    mem.get(key)?.let { return@withContext it }

                    val file = cacheFile(ctx, absolute)
                    var bitmap = if (file.exists()) decode(file, widthPx, heightPx) else null

                    if (bitmap == null) {
                        val bytes = download(absolute)
                        if (bytes != null) {
                            file.parentFile?.let { trimDisk(it) }
                            runCatching { file.writeBytes(bytes) }
                            bitmap = decodeBytes(bytes, widthPx, heightPx)
                        }
                    }
                    if (bitmap != null) mem.put(key, bitmap)
                    bitmap
                }
            } finally {
                synchronized(locks) { locks.remove(key) }
            }
        }

    /** 内存缓存清空。设置页里改了地址时可以调，这里保留成公开方法。 */
    fun clearMemory() {
        mem.evictAll()
    }

    // ------------------------------------------------------------------ 内部

    private fun keyOf(url: String, w: Int, h: Int): String = "$url@${w}x$h"

    private fun cacheFile(ctx: Context, url: String): File {
        val dir = File(ctx.cacheDir, "covers").apply { if (!exists()) mkdirs() }
        return File(dir, sha1(url))
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * 把可能是相对路径的图片地址补全。
     * 接口里常见三种写法：完整 URL、`//host/path`（省协议）、`/static/x.jpg`（同源）。
     */
    private fun absolute(url: String, base: String): String {
        val u = url.trim()
        return when {
            u.startsWith("http://") || u.startsWith("https://") -> u
            u.startsWith("//") -> "https:$u"
            u.startsWith("/") && base.isNotBlank() -> {
                val b = base.trim().trimEnd('/')
                val scheme = if (b.startsWith("https://")) "https" else "http"
                val host = b.substringAfter("://").substringBefore('/')
                "$scheme://$host$u"
            }
            else -> u
        }
    }

    private fun download(url: String): ByteArray? = try {
        // Referer 用图片自己的域名根，防盗链配置基本都是这么判的
        val referer = runCatching {
            val u = URL(url)
            "${u.protocol}://${u.host}/"
        }.getOrDefault("")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "image/*,*/*;q=0.8")
            if (referer.isNotBlank()) setRequestProperty("Referer", referer)
            // 有的图床挂了鉴权，token 也一起带上，不需要的后端会忽略
            ApiConfigStore.config.token.takeIf { it.isNotBlank() }?.let {
                setRequestProperty("Authorization", it)
            }
        }
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    private fun decode(file: File, reqW: Int, reqH: Int): Bitmap? =
        runCatching { decodeBytes(file.readBytes(), reqW, reqH) }.getOrNull()

    /**
     * 解码并降采样。先只读尺寸（inJustDecodeBounds），算出能把图缩到刚好覆盖
     * 目标格子的 2 的幂，再真正解码——这样一张 1600×2200 的封面在 400px 的格子里
     * 只会占 400×550 左右的内存。
     */
    private fun decodeBytes(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val safeW = reqW.coerceAtLeast(1)
        val safeH = reqH.coerceAtLeast(1)
        while (bounds.outWidth / (sample * 2) >= safeW && bounds.outHeight / (sample * 2) >= safeH) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }.getOrNull()
    }

    /** 磁盘缓存超限时清空。由写入方在后台调用，不阻塞界面。 */
    private fun trimDisk(dir: File) {
        val total = dir.listFiles()?.sumOf { it.length() } ?: 0L
        if (total > DISK_LIMIT) dir.listFiles()?.forEach { it.delete() }
    }
}
