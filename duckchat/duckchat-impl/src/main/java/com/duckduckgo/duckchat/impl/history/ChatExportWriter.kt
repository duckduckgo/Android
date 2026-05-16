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

package com.duckduckgo.duckchat.impl.history

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.DownloadsRepository
import com.duckduckgo.downloads.api.model.DownloadItem
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.io.File
import javax.inject.Inject

/**
 * Writes a formatted chat export to the public Downloads directory and registers it in the app's
 * Downloads database so it appears in the in-app Downloads screen.
 */
interface ChatExportWriter {
    suspend fun write(text: String, displayTitle: String): File
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealChatExportWriter @Inject constructor(
    private val context: Context,
    private val downloadsRepository: DownloadsRepository,
) : ChatExportWriter {

    override suspend fun write(text: String, displayTitle: String): File {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).also { it.mkdirs() }
        val file = resolveAvailableFile(dir, sanitize(displayTitle))
        file.writeText(text)

        downloadsRepository.insert(
            DownloadItem(
                downloadId = 0L,
                downloadStatus = DOWNLOAD_FINISHED,
                fileName = file.name,
                contentLength = file.length(),
                createdAt = DatabaseDateFormatter.timestamp(),
                filePath = file.absolutePath,
            ),
        )

        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("text/plain"), null)
        return file
    }

    private fun sanitize(title: String): String {
        val cleaned = title.trim().replace(UNSAFE_CHARS, "_")
        return cleaned.ifBlank { "chat" }
    }

    /** Mirrors :downloads-impl FilenameExtractor.addCountSuffix — appends `-1`, `-2`, ... before the extension. */
    private fun resolveAvailableFile(dir: File, baseName: String): File {
        val initial = File(dir, "$baseName.$EXTENSION")
        if (!initial.exists()) return initial
        var count = 1
        while (true) {
            val candidate = File(dir, "$baseName-$count.$EXTENSION")
            if (!candidate.exists()) return candidate
            count++
        }
    }

    private companion object {
        const val DOWNLOAD_FINISHED = 1 // mirrors com.duckduckgo.downloads.store.DownloadStatus.FINISHED
        const val EXTENSION = "txt"
        val UNSAFE_CHARS = Regex("[\\\\/:*?\"<>|\\r\\n\\t]")
    }
}
