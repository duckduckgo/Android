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

package com.duckduckgo.app.bookmarks.service

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.FolderBranch
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.TreeNode
import com.duckduckgo.savedsites.api.service.ExportSavedSitesResult
import com.duckduckgo.savedsites.impl.RealSavedSitesRepository
import com.duckduckgo.savedsites.impl.service.FolderTreeItem
import com.duckduckgo.savedsites.impl.service.RealSavedSitesExporter
import com.duckduckgo.savedsites.impl.service.RealSavedSitesParser
import com.duckduckgo.savedsites.store.Relation
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SavedSitesExporterTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var savedSitesRepository: SavedSitesRepository
    private lateinit var exporter: RealSavedSitesExporter
    private lateinit var savedSitesEntitiesDao: SavedSitesEntitiesDao
    private lateinit var savedSitesRelationsDao: SavedSitesRelationsDao
    private lateinit var filesDir: File

    @Before
    fun before() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        savedSitesEntitiesDao = db.syncEntitiesDao()
        savedSitesRelationsDao = db.syncRelationsDao()
        savedSitesRepository = RealSavedSitesRepository(savedSitesEntitiesDao, savedSitesRelationsDao)

        filesDir = context.filesDir
        exporter = RealSavedSitesExporter(context.contentResolver, savedSitesRepository, RealSavedSitesParser())

        // initial db state
        savedSitesRepository.insert(BookmarkFolder(id = Relation.BOOMARKS_ROOT, name = "Bookmarks", parentId = ""))
        savedSitesRepository.insert(BookmarkFolder(id = Relation.FAVORITES_ROOT, name = "Favorites", parentId = ""))
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenSomeBookmarksExistThenExportingSucceeds() = runTest {
        val root = BookmarkFolder(Relation.BOOMARKS_ROOT, "DuckDuckGo Bookmarks", "")
        val parentFolder = BookmarkFolder("folder1", "Folder One", Relation.BOOMARKS_ROOT)
        val childFolder = BookmarkFolder("folder2", "Folder Two", "folder1")
        val childBookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2")
        val folderBranch = FolderBranch(listOf(childBookmark), listOf(root, parentFolder, childFolder))

        savedSitesRepository.insertFolderBranch(folderBranch)

        savedSitesRepository.insertFavorite("www.favorite.com", "Favorite")

        val testFile = File(filesDir, "test_bookmarks.html")
        val localUri = Uri.fromFile(testFile)

        val result = exporter.export(localUri)
        testFile.delete()

        assertTrue(result is ExportSavedSitesResult.Success)
    }

    @Test
    fun whenFileDoesNotExistThenExportingFails() = runTest {
        savedSitesRepository.insertBookmark("www.example.com", "example")

        val localUri = Uri.parse("uridoesnotexist")

        val result = exporter.export(localUri)

        assertTrue(result is ExportSavedSitesResult.Error)
    }

    @Test
    fun whenNoSavedSitesExistThenNothingIsExported() = runTest {
        val localUri = Uri.parse("whatever")
        val result = exporter.export(localUri)
        assertTrue(result is ExportSavedSitesResult.NoSavedSitesExported)
    }

    @Test
    fun whenSomeFavoritesExistThenExportingSucceeds() = runTest {
        val favorite = SavedSite.Favorite(id = "favorite1", title = "example", url = "www.example.com", position = 0)
        savedSitesRepository.insert(favorite)

        val testFile = File(filesDir, "test_favorites.html")
        val localUri = Uri.fromFile(testFile)

        val result = exporter.export(localUri)
        testFile.delete()

        assertTrue(result is ExportSavedSitesResult.Success)
    }

    @Test
    fun whenGetTreeStructureThenReturnTraversableTree() = runTest {
        val root = BookmarkFolder(Relation.BOOMARKS_ROOT, "DuckDuckGo Bookmarks", "")
        val parentFolder = BookmarkFolder("folder1", "Folder One", Relation.BOOMARKS_ROOT)
        val childFolder = BookmarkFolder("folder2", "Folder Two", "folder1")
        val childBookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2")
        val folderBranch = FolderBranch(listOf(childBookmark), listOf(root, parentFolder, childFolder))

        savedSitesRepository.insertFolderBranch(folderBranch)

        val itemList = listOf(root, parentFolder, childFolder, childBookmark)
        val preOrderList = listOf(childBookmark, childFolder, parentFolder, root)

        val treeStructure = exporter.getTreeFolderStructure()

        var count = 0
        var preOrderCount = 0

        treeStructure.forEachVisit(
            { node ->
                testNode(node, itemList, count)
                count++
            },
            { node ->
                testNode(node, preOrderList, preOrderCount)
                preOrderCount++
            },
        )
    }

    private fun testNode(
        node: TreeNode<FolderTreeItem>,
        itemList: List<Any>,
        count: Int,
    ) {
        if (node.value.url != null) {
            val entity = itemList[count] as Bookmark

            Assert.assertEquals(entity.title, node.value.name)
            Assert.assertEquals(entity.id, node.value.id)
            Assert.assertEquals(entity.parentId, node.value.parentId)
            Assert.assertEquals(entity.url, node.value.url)
            Assert.assertEquals(3, node.value.depth)
        } else {
            val entity = itemList[count] as BookmarkFolder

            Assert.assertEquals(entity.name, node.value.name)
            Assert.assertEquals(entity.id, node.value.id)
            Assert.assertEquals(entity.parentId, node.value.parentId)

            when (node.value.parentId) {
                "" -> {
                    Assert.assertEquals(0, node.value.depth)
                }
                Relation.BOOMARKS_ROOT -> {
                    Assert.assertEquals(1, node.value.depth)
                }
                else -> {
                    Assert.assertEquals(2, node.value.depth)
                }
            }
        }
    }
}
