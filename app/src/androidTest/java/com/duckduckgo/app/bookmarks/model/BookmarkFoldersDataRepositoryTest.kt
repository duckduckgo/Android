/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.bookmarks.model

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.bookmarks.db.*
import com.duckduckgo.app.global.db.AppDatabase
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class BookmarkFoldersDataRepositoryTest {
    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var bookmarkFoldersDao: BookmarkFoldersDao
    private lateinit var bookmarksDao: BookmarksDao
    private lateinit var repository: BookmarkFoldersRepository

    private var mockBookmarkFoldersDao: BookmarkFoldersDao = mock()

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bookmarkFoldersDao = db.bookmarkFoldersDao()
        bookmarksDao = db.bookmarksDao()
        repository = BookmarkFoldersDataRepository(bookmarkFoldersDao, bookmarksDao, db)
    }

    @Test
    fun whenInsertBookmarkFolderThenReturnId() = runBlocking {
        val id = repository.insert(BookmarkFolder(id = 1, name = "name", parentId = 0))
        assertEquals(1, id)
    }

    @Test
    fun whenUpdateBookmarkFolderThenUpdateBookmarkFolderCalled() = runBlocking {
        repository = BookmarkFoldersDataRepository(mockBookmarkFoldersDao, bookmarksDao, db)
        val bookmarkFolder = BookmarkFolder(id = 1, name = "name", parentId = 0)
        repository.update(bookmarkFolder)

        verify(mockBookmarkFoldersDao).update(BookmarkFolderEntity(id = bookmarkFolder.id, name = bookmarkFolder.name, parentId = bookmarkFolder.parentId))
    }

    @Test
    fun whenGetBookmarkFolderBranchThenReturnFoldersAndBookmarksForBranch() = runBlocking {
        val parentFolder = BookmarkFolderEntity(id = 1, name = "name", parentId = 0)
        val childFolder = BookmarkFolderEntity(id = 2, name = "another name", parentId = 1)
        val childBookmark = BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 1)

        repository.insertFolderBranch(BookmarkFolderBranch(listOf(childBookmark), listOf(parentFolder, childFolder)))

        val branch = repository.getBookmarkFolderBranch(BookmarkFolder(parentFolder.id, parentFolder.name, parentFolder.parentId))

        assertEquals(listOf(childBookmark), branch.bookmarkEntities)
        assertEquals(listOf(parentFolder, childFolder), branch.bookmarkFolderEntities)
    }

    @Test
    fun whenGetBranchFoldersThenReturnFolderListForBranch() = runBlocking {
        val parentFolderEntity = BookmarkFolderEntity(id = 1, name = "name", parentId = 0)
        val childFolderEntity = BookmarkFolderEntity(id = 2, name = "another name", parentId = 1)

        bookmarkFoldersDao.insertList(listOf(parentFolderEntity, childFolderEntity))

        val parentFolder = BookmarkFolder(parentFolderEntity.id, parentFolderEntity.name, parentFolderEntity.parentId)
        val childFolder = BookmarkFolder(childFolderEntity.id, childFolderEntity.name, childFolderEntity.parentId)

        val list = repository.getBranchFolders(parentFolder)

        assertEquals(listOf(parentFolder, childFolder), list)
    }

    @Test
    fun whenDeleteFolderBranchThenDeletedBookmarksAndFoldersAreNoLongerInDB() = runBlocking {
        val parentFolderEntity = BookmarkFolderEntity(id = 1, name = "name", parentId = 0)
        val childFolderEntity = BookmarkFolderEntity(id = 2, name = "another name", parentId = 1)
        val childBookmark = BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 1)

        val branchToDelete = BookmarkFolderBranch(listOf(childBookmark), listOf(parentFolderEntity, childFolderEntity))

        repository.insertFolderBranch(branchToDelete)
        repository.deleteFolderBranch(branchToDelete)

        assertFalse(bookmarksDao.hasBookmarks())
        assertTrue(bookmarkFoldersDao.getBookmarkFoldersImmediate().isEmpty())
    }

    @Test
    fun whenBuildFlatStructureThenReturnFolderListWithDepth() = runBlocking {
        val parentFolder = BookmarkFolder(id = 1, name = "name", parentId = 0)
        val childFolder = BookmarkFolder(id = 2, name = "another name", parentId = 1)
        val folder = BookmarkFolder(id = 3, name = "folder name", parentId = 0)

        bookmarkFoldersDao.insertList(
            listOf(
                BookmarkFolderEntity(parentFolder.id, parentFolder.name, parentFolder.parentId),
                BookmarkFolderEntity(childFolder.id, childFolder.name, childFolder.parentId),
                BookmarkFolderEntity(folder.id, folder.name, folder.parentId)
            )
        )

        val flatStructure = repository.buildFlatStructure(3, null, "Bookmarks")

        val items = listOf(
            BookmarkFolderItem(0, BookmarkFolder(0, "Bookmarks", -1), false),
            BookmarkFolderItem(1, parentFolder, false),
            BookmarkFolderItem(2, childFolder, false),
            BookmarkFolderItem(1, folder, true)
        )

        assertEquals(items, flatStructure)
    }

    @Test
    fun whenBuildFlatStructureThenReturnFolderListWithDepthWithoutCurrentFolderBranch() = runBlocking {
        val parentFolder = BookmarkFolder(id = 1, name = "name", parentId = 0)
        val childFolder = BookmarkFolder(id = 2, name = "another name", parentId = 1)
        val folder = BookmarkFolder(id = 3, name = "folder name", parentId = 0)

        bookmarkFoldersDao.insertList(
            listOf(
                BookmarkFolderEntity(parentFolder.id, parentFolder.name, parentFolder.parentId),
                BookmarkFolderEntity(childFolder.id, childFolder.name, childFolder.parentId),
                BookmarkFolderEntity(folder.id, folder.name, folder.parentId)
            )
        )

        val flatStructure = repository.buildFlatStructure(3, parentFolder, "Bookmarks")

        val items = listOf(
            BookmarkFolderItem(0, BookmarkFolder(0, "Bookmarks", -1), false),
            BookmarkFolderItem(1, folder, true)
        )

        assertEquals(items, flatStructure)
    }

    @Test
    fun whenBuildTreeStructureThenReturnTraversableTree() = runBlocking {
        val root = BookmarkFolderEntity(id = 0, name = "DuckDuckGo Bookmarks", parentId = -1)
        val parentFolder = BookmarkFolderEntity(id = 1, name = "name", parentId = 0)
        val childFolder = BookmarkFolderEntity(id = 2, name = "another name", parentId = 1)
        val childBookmark = BookmarkEntity(id = 1, title = "title", url = "www.example.com", parentId = 1)

        val folderList = listOf(parentFolder, childFolder)

        repository.insertFolderBranch(BookmarkFolderBranch(listOf(childBookmark), folderList))

        val itemList = listOf(root, parentFolder, childFolder, childBookmark)
        val preOrderList = listOf(childFolder, childBookmark, parentFolder, root)

        val treeStructure = repository.buildTreeStructure()

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
            }
        )
    }

    private fun testNode(node: TreeNode<FolderTreeItem>, itemList: List<Any>, count: Int) {
        if (node.value.url != null) {
            val entity = itemList[count] as BookmarkEntity

            assertEquals(entity.title, node.value.name)
            assertEquals(entity.id, node.value.id)
            assertEquals(entity.parentId, node.value.parentId)
            assertEquals(entity.url, node.value.url)
            assertEquals(2, node.value.depth)
        } else {
            val entity = itemList[count] as BookmarkFolderEntity

            assertEquals(entity.name, node.value.name)
            assertEquals(entity.id, node.value.id)
            assertEquals(entity.parentId, node.value.parentId)

            when (node.value.parentId) {
                -1L -> {
                    assertEquals(0, node.value.depth)
                }
                0L -> {
                    assertEquals(1, node.value.depth)
                }
                else -> {
                    assertEquals(2, node.value.depth)
                }
            }
        }
    }
}
