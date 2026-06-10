/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.browser.pdf

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.logcat
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/**
 * Copies a file already present in app cache (or another readable [Uri]) into the device's
 * public Downloads folder.
 *
 * Designed to be reused for any feature that needs to "save a cached resource to Downloads",
 * not just PDFs — name kept generic on purpose.
 */
interface CachedFileDownloader {

    /**
     * Copies the bytes available at [cachedFileUri] into the public Downloads folder under
     * [fileName] with the given [mimeType].
     *
     * Returns the absolute file system path on success, or `null` if the copy failed. The path
     * (rather than a `content://` URI) is what callers need — DuckDuckGo's Downloads screen
     * filters out entries whose `File(filePath).exists()` returns false on each open, and the
     * post-download snackbar's "Open" action constructs a `File` from the path.
     */
    suspend fun saveToDownloads(cachedFileUri: Uri, fileName: String, mimeType: String): String?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealCachedFileDownloader @Inject constructor(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) : CachedFileDownloader {

    override suspend fun saveToDownloads(cachedFileUri: Uri, fileName: String, mimeType: String): String? =
        withContext(dispatcherProvider.io()) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    saveViaMediaStore(cachedFileUri, fileName, mimeType)
                } else {
                    saveDirectly(cachedFileUri, fileName)
                }
            } catch (e: IOException) {
                logcat { "CachedFileDownloader save failed: ${e.message}" }
                null
            } catch (e: SecurityException) {
                logcat { "CachedFileDownloader save denied: ${e.message}" }
                null
            }
        }

    @RequiresApi(29)
    private fun saveViaMediaStore(cachedFileUri: Uri, fileName: String, mimeType: String): String? {
        val resolver = context.contentResolver
        val uniqueName = resolveUniqueName(fileName) { isDisplayNameTaken(resolver, it) }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, uniqueName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null

        resolver.openOutputStream(targetUri)?.use { output ->
            openCachedInput(cachedFileUri)?.use { it.copyTo(output) } ?: return null
        } ?: return null

        val finalize = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
        resolver.update(targetUri, finalize, null, null)

        return resolveFilePath(targetUri)
    }

    private fun saveDirectly(cachedFileUri: Uri, fileName: String): String? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val uniqueName = resolveUniqueName(fileName) { File(downloadsDir, it).exists() }
        val targetFile = File(downloadsDir, uniqueName)
        openCachedInput(cachedFileUri)?.use { input ->
            targetFile.outputStream().use { input.copyTo(it) }
        } ?: return null
        return targetFile.absolutePath
    }

    /**
     * Returns the first available `name-N.ext` variant of [fileName] for which [isTaken]
     * is false (or [fileName] itself if it isn't taken). Format matches FilenameExtractor
     * so DDG's Downloads screen shows the same suffix shape regardless of which path
     * (MediaStore on API 29+, direct File I/O on pre-Q) wrote the file.
     */
    private fun resolveUniqueName(fileName: String, isTaken: (String) -> Boolean): String {
        if (!isTaken(fileName)) return fileName
        val (base, ext) = fileName.splitFileNameAndExtension()
        var i = 1
        while (true) {
            val candidate = "$base-$i$ext"
            if (!isTaken(candidate)) return candidate
            i++
        }
    }

    @RequiresApi(29)
    private fun isDisplayNameTaken(resolver: android.content.ContentResolver, displayName: String): Boolean {
        return resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads.DISPLAY_NAME),
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(displayName),
            null,
        )?.use { it.count > 0 } ?: false
    }

    private fun openCachedInput(cachedFileUri: Uri): InputStream? {
        return if (cachedFileUri.scheme == "file" || cachedFileUri.scheme == null) {
            cachedFileUri.path?.let { File(it).inputStream() }
        } else {
            context.contentResolver.openInputStream(cachedFileUri)
        }
    }

    private fun resolveFilePath(contentUri: Uri): String? {
        return context.contentResolver.query(
            contentUri,
            arrayOf(MediaStore.MediaColumns.DATA),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).takeIf { !it.isNullOrBlank() } else null
        }
    }

    private fun String.splitFileNameAndExtension(): Pair<String, String> {
        val dot = lastIndexOf('.')
        return if (dot >= 0) substring(0, dot) to substring(dot) else this to ""
    }
}
