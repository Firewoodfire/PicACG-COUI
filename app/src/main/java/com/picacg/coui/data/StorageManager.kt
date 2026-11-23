package com.picacg.coui.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** 一类真实文件占用的空间。 */
data class StorageUsage(
    val bytes: Long,
    val files: Int,
)

/** 下载目录里的一个文件；[path] 只用于删除，不在界面暴露完整路径。 */
data class DownloadEntry(
    val name: String,
    val path: String,
    val bytes: Long,
    val modified: Long,
)

data class StorageSnapshot(
    val coverCache: StorageUsage,
    val otherCache: StorageUsage,
    val downloads: StorageUsage,
    val downloadFiles: List<DownloadEntry>,
) {
    val cacheBytes: Long get() = coverCache.bytes + otherCache.bytes
    val totalBytes: Long get() = cacheBytes + downloads.bytes
}

/**
 * 储存空间管理。只操作 App 自己的私有目录，不需要存储权限，也不会碰用户相册或其他 App 文件。
 *
 * 当前版本没有漫画下载器，但仍会如实列出下载目录里已经存在的文件；没有文件就显示 0 B。
 */
object StorageManager {

    suspend fun snapshot(context: Context): StorageSnapshot = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val covers = File(app.cacheDir, "covers")
        val coverFiles = covers.listFiles().orEmpty().filter { it.isFile }
        val otherFiles = app.cacheDir.walkTopDown()
            .filter { it.isFile && !it.canonicalPath.startsWith(covers.canonicalPath + File.separator) }
            .toList()
        val roots = downloadRoots(app)
        val downloads = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile }.map { file ->
                DownloadEntry(
                    name = file.relativeTo(root).path,
                    path = file.absolutePath,
                    bytes = file.length(),
                    modified = file.lastModified(),
                )
            }
        }.sortedByDescending { it.modified }

        StorageSnapshot(
            coverCache = StorageUsage(coverFiles.sumOf { it.length() }, coverFiles.size),
            otherCache = StorageUsage(otherFiles.sumOf { it.length() }, otherFiles.size),
            downloads = StorageUsage(downloads.sumOf { it.bytes }, downloads.size),
            downloadFiles = downloads,
        )
    }

    suspend fun clearCoverCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        CoverLoader.clearMemory()
        deleteContents(File(context.cacheDir, "covers"))
    }

    suspend fun clearOtherCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        val covers = File(context.cacheDir, "covers").canonicalPath
        var ok = true
        context.cacheDir.listFiles().orEmpty().forEach { file ->
            if (file.canonicalPath != covers) ok = deleteRecursively(file) && ok
        }
        ok
    }

    suspend fun clearDownloads(context: Context): Boolean = withContext(Dispatchers.IO) {
        var ok = true
        downloadRoots(context).forEach { ok = deleteContents(it) && ok }
        ok
    }

    suspend fun deleteDownload(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        val roots = downloadRoots(context)
        val allowed = roots.any { root ->
            val prefix = root.canonicalPath + File.separator
            file.canonicalPath.startsWith(prefix)
        }
        allowed && file.isFile && file.delete()
    }

    private fun downloadRoots(context: Context): List<File> = buildList {
        add(File(context.filesDir, "downloads"))
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let(::add)
    }.distinctBy { it.absolutePath }

    private fun deleteContents(dir: File): Boolean {
        if (!dir.exists()) return true
        var ok = true
        dir.listFiles().orEmpty().forEach { ok = deleteRecursively(it) && ok }
        return ok
    }

    private fun deleteRecursively(file: File): Boolean {
        var ok = true
        if (file.isDirectory) file.listFiles().orEmpty().forEach { ok = deleteRecursively(it) && ok }
        return file.delete() && ok
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        return if (unit == 0) "$bytes B"
        else String.format(Locale.CHINA, if (value >= 100) "%.0f %s" else "%.1f %s", value, units[unit])
    }
}
