package com.zame.nutrinest.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Copies images picked with the Photo Picker into the app's private storage.
 * Photo Picker URIs are short-lived, so we must own a copy for the image to
 * still exist next time the app opens.
 */
object ImageStorage {

    private const val MAX_IMAGE_BYTES = 20L * 1024 * 1024

    suspend fun copyToAppStorage(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_IMAGE_BYTES) { "Selected image is too large" }
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: return@withContext null
                file.absolutePath
            }.getOrElse {
                file.delete()
                null
            }
        }

    /** Deletes only files owned by this app's managed image directory. */
    suspend fun deleteManaged(context: Context, path: String?): Boolean = withContext(Dispatchers.IO) {
        deleteManagedNow(context, path)
    }

    fun deleteManagedNow(context: Context, path: String?): Boolean {
        val candidate = path?.let(::File) ?: return false
        return runCatching {
            val imagesDir = File(context.filesDir, "images").canonicalFile
            val resolved = candidate.canonicalFile
            resolved.parentFile == imagesDir && (!resolved.exists() || resolved.delete())
        }.getOrDefault(false)
    }
}
