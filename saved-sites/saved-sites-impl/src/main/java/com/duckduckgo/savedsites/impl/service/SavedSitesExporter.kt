/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.savedsites.impl.service

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.api.models.TreeNode
import com.duckduckgo.savedsites.api.service.ExportSavedSitesResult
import com.duckduckgo.savedsites.api.service.SavedSitesExporter
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.withContext

class RealSavedSitesExporter(
    private val contentResolver: ContentResolver,
    private val savedSitesRepository: SavedSitesRepository,
    private val savedSitesParser: SavedSitesParser,
    private val dispatcher: DispatcherProvider = DefaultDispatcherProvider(),
) : SavedSitesExporter {

    override suspend fun export(uri: Uri): ExportSavedSitesResult {
        val favorites = withContext(dispatcher.io()) {
            savedSitesRepository.getFavoritesSync()
        }
        val treeStructure = withContext(dispatcher.io()) {
            getTreeFolderStructure()
        }
        val html = savedSitesParser.generateHtml(treeStructure, favorites)
        return storeHtml(uri, html)
    }

    private fun storeHtml(
        uri: Uri,
        content: String,
    ): ExportSavedSitesResult {
        return try {
            if (content.isEmpty()) {
                return ExportSavedSitesResult.NoSavedSitesExported
            }
            val file = contentResolver.openFileDescriptor(uri, "w")
            if (file != null) {
                val fileOutputStream = FileOutputStream(file.fileDescriptor)
                fileOutputStream.write(content.toByteArray())
                fileOutputStream.close()
                file.close()
                ExportSavedSitesResult.Success
            } else {
                ExportSavedSitesResult.NoSavedSitesExported
            }
        } catch (e: FileNotFoundException) {
            ExportSavedSitesResult.Error(e)
        } catch (e: IOException) {
            ExportSavedSitesResult.Error(e)
        }
    }

    @VisibleForTesting
    suspend fun getTreeFolderStructure(): TreeNode<FolderTreeItem> {
        val node = TreeNode(FolderTreeItem(SavedSitesNames.BOOKMARKS_ROOT, RealSavedSitesParser.BOOKMARKS_FOLDER, "", null, 0))
        populateNode(node, SavedSitesNames.BOOKMARKS_ROOT, 1)
        return node
    }

    private suspend fun populateNode(
        parentNode: TreeNode<FolderTreeItem>,
        parentId: String,
        currentDepth: Int,
    ) {
        val folderContent = savedSitesRepository.getFolderContentSync(parentId)
        folderContent.second.forEach { bookmarkFolder ->
            val childNode = TreeNode(FolderTreeItem(bookmarkFolder.id, bookmarkFolder.name, bookmarkFolder.parentId, null, currentDepth))
            parentNode.add(childNode)
            populateNode(childNode, bookmarkFolder.id, currentDepth + 1)
        }

        folderContent.first.forEach { bookmark ->
            bookmark.title?.let { title ->
                val childNode = TreeNode(FolderTreeItem(bookmark.id, title, bookmark.parentId, bookmark.url, currentDepth))
                parentNode.add(childNode)
            }
        }
    }
}

data class FolderTreeItem(
    val id: String,
    val name: String,
    val parentId: String,
    val url: String?,
    val depth: Int,
)

typealias FolderTree = TreeNode<FolderTreeItem>
