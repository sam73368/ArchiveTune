/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class TvBackupFile(
    val name: String,
    val uri: Uri,
    val sizeBytes: Long,
    val modifiedMillis: Long,
    val inDownloads: Boolean,
)

object TvBackupStorage {
    const val DOWNLOADS_FOLDER = "ArchiveTune"
    private const val PRIVATE_FOLDER = "tv_backups"
    private const val MIME_TYPE = "application/octet-stream"
    private val extensionSuffix = ".${BackupPlatform.TV_FILE_EXTENSION}"

    fun directory(context: Context): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        return File(root, PRIVATE_FOLDER).also { it.mkdirs() }
    }

    fun newFileName(): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        return "ArchiveTune_TV_$stamp$extensionSuffix"
    }

    fun list(context: Context): List<TvBackupFile> {
        val downloads = queryDownloads(context)
        val downloadNames = downloads.mapTo(HashSet()) { it.name }
        val local =
            directory(context)
                .listFiles { file -> file.isFile && file.name.endsWith(extensionSuffix, ignoreCase = true) }
                .orEmpty()
                .map { file ->
                    TvBackupFile(
                        name = file.name,
                        uri = Uri.fromFile(file),
                        sizeBytes = file.length(),
                        modifiedMillis = file.lastModified(),
                        inDownloads = file.name in downloadNames,
                    )
                }
        val localNames = local.mapTo(HashSet()) { it.name }
        return (local + downloads.filter { it.name !in localNames })
            .sortedByDescending { it.modifiedMillis }
    }

    fun exportToDownloads(
        context: Context,
        source: File,
    ): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportWithMediaStore(context, source)
            } else {
                val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DOWNLOADS_FOLDER)
                folder.mkdirs()
                source.copyTo(File(folder, source.name), overwrite = true)
                true
            }
        }.getOrDefault(false)

    fun delete(
        context: Context,
        name: String,
    ) {
        runCatching { File(directory(context), name).delete() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                    arrayOf(name),
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportWithMediaStore(
        context: Context,
        source: File,
    ): Boolean {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, source.name)
                put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOADS_FOLDER")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val output = resolver.openOutputStream(target) ?: error("No output stream")
            output.use { stream -> source.inputStream().use { input -> input.copyTo(stream) } }
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(target, done, null, null)
            true
        } catch (error: Exception) {
            runCatching { resolver.delete(target, null, null) }
            false
        }
    }

    private fun queryDownloads(context: Context): List<TvBackupFile> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val result = ArrayList<TvBackupFile>()
        runCatching {
            context.contentResolver
                .query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Downloads._ID,
                        MediaStore.Downloads.DISPLAY_NAME,
                        MediaStore.Downloads.SIZE,
                        MediaStore.Downloads.DATE_MODIFIED,
                    ),
                    "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$extensionSuffix"),
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        result +=
                            TvBackupFile(
                                name = cursor.getString(1).orEmpty(),
                                uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                                sizeBytes = cursor.getLong(2),
                                modifiedMillis = cursor.getLong(3) * 1000L,
                                inDownloads = true,
                            )
                    }
                }
        }
        return result
    }
}
