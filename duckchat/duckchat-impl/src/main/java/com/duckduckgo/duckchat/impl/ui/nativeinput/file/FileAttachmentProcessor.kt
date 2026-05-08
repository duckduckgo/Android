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

package com.duckduckgo.duckchat.impl.ui.nativeinput.file

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

interface FileAttachmentProcessor {
    suspend fun processFile(context: Context, uri: Uri): FileAttachment?
}

@ContributesBinding(AppScope::class)
class RealFileAttachmentProcessor @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
) : FileAttachmentProcessor {

    override suspend fun processFile(context: Context, uri: Uri): FileAttachment? = withContext(dispatcherProvider.io()) {
        runCatching {
            val (fileName, fileSize) = resolveFileInfo(context, uri) ?: return@withContext null
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            val pageCount = if (mimeType == "application/pdf") {
                runCatching {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        PdfRenderer(pfd).use { it.pageCount }
                    }
                }.getOrNull()
            } else {
                null
            }

            FileAttachment(
                id = UUID.randomUUID().toString(),
                uri = uri,
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = fileSize,
                base64Data = base64,
                pageCount = pageCount,
            )
        }.getOrNull()
    }

    private fun resolveFileInfo(context: Context, uri: Uri): Pair<String, Long>? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else uri.lastPathSegment ?: "file"
            val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
            name to size
        }
    }
}
