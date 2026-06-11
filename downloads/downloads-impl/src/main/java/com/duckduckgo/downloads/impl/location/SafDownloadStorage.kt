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

package com.duckduckgo.downloads.impl.location

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

interface SafDownloadStorage {
    fun takePersistableTreePermission(treeUri: Uri, takeFlags: Int)

    fun isTreeAccessible(treeUri: Uri): Boolean

    fun getTreeDisplayName(treeUri: Uri): String?

    fun buildPathLabel(treeUri: Uri): String

    fun fileExists(treeUri: Uri, fileName: String): Boolean

    fun resolveUniqueFileName(treeUri: Uri, fileName: String): String

    fun createFile(treeUri: Uri, fileName: String, mimeType: String?): DocumentFile?

    fun deleteFile(fileUri: Uri): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealSafDownloadStorage @Inject constructor(
    private val context: Context,
) : SafDownloadStorage {

    override fun takePersistableTreePermission(treeUri: Uri, takeFlags: Int) {
        context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
    }

    override fun isTreeAccessible(treeUri: Uri): Boolean {
        return runCatching {
            val documentFile = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            documentFile.canWrite() && documentFile.exists()
        }.getOrElse {
            logcat { "SAF tree not accessible: ${it.asLog()}" }
            false
        }
    }

    override fun getTreeDisplayName(treeUri: Uri): String? {
        return runCatching {
            DocumentFile.fromTreeUri(context, treeUri)?.name
        }.getOrNull()
    }

    override fun buildPathLabel(treeUri: Uri): String {
        val displayName = getTreeDisplayName(treeUri)
        if (displayName.isNullOrBlank()) {
            return treeUri.lastPathSegment.orEmpty()
        }
        return displayName
    }

    override fun fileExists(treeUri: Uri, fileName: String): Boolean {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        return tree.findFile(fileName)?.exists() == true
    }

    override fun resolveUniqueFileName(treeUri: Uri, fileName: String): String {
        if (!fileExists(treeUri, fileName)) return fileName

        val dotIndex = fileName.lastIndexOf('.')
        val (baseName, extension) = if (dotIndex > 0) {
            fileName.substring(0, dotIndex) to fileName.substring(dotIndex)
        } else {
            fileName to ""
        }

        var count = 1
        var candidate: String
        do {
            candidate = if (extension.isEmpty()) {
                "$baseName-$count"
            } else {
                "$baseName-$count$extension"
            }
            count++
        } while (fileExists(treeUri, candidate))

        return candidate
    }

    override fun createFile(treeUri: Uri, fileName: String, mimeType: String?): DocumentFile? {
        return runCatching {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            val resolvedMimeType = mimeType?.takeIf { it.isNotBlank() } ?: MIME_TYPE_OCTET_STREAM
            tree.createFile(resolvedMimeType, fileName)
        }.getOrElse {
            logcat { "Failed to create SAF file: ${it.asLog()}" }
            null
        }
    }

    override fun deleteFile(fileUri: Uri): Boolean {
        return runCatching {
            DocumentFile.fromSingleUri(context, fileUri)?.delete() == true
        }.getOrDefault(false)
    }

    companion object {
        private const val MIME_TYPE_OCTET_STREAM = "application/octet-stream"
    }
}

fun Uri.isDownloadsTreeUri(): Boolean {
    return DocumentsContract.isTreeUri(this)
}
