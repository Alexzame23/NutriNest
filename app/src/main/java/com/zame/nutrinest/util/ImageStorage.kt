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

    suspend fun copyToAppStorage(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, "images").apply { mkdirs() }
                val file = File(dir, "${UUID.randomUUID()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
                file.absolutePath
            }.getOrNull()
        }
}
