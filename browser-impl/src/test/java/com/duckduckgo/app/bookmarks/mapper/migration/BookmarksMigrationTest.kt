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

package com.duckduckgo.app.bookmarks.mapper.migration

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarkFolderEntity
import com.duckduckgo.app.bookmarks.db.BookmarkFoldersDao
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.db.FavoriteEntity
import com.duckduckgo.app.bookmarks.db.FavoritesDao
import com.duckduckgo.app.bookmarks.migration.AppDatabaseBookmarksMigrationCallback
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.appbuildconfig.api.*
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.store.*
import com.duckduckgo.savedsites.store.EntityType.BOOKMARK
import junit.framework.Assert.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BookmarksMigrationTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var appDatabase: AppDatabase

    private lateinit var savedSitesEntitiesDao: SavedSitesEntitiesDao
    private lateinit var savedSitesRelationsDao: SavedSitesRelationsDao

    private lateinit var favoritesDao: FavoritesDao
    private lateinit var bookmarksDao: BookmarksDao
    private lateinit var bookmarkFoldersDao: BookmarkFoldersDao

    private var appBuildConfig: AppBuildConfig = mock()

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

        appDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        favoritesDao = appDatabase.favoritesDao()
        bookmarksDao = appDatabase.bookmarksDao()
        bookmarkFoldersDao = appDatabase.bookmarkFoldersDao()

        savedSitesEntitiesDao = appDatabase.syncEntitiesDao()
        savedSitesRelationsDao = appDatabase.syncRelationsDao()
    }

    @After
    fun tearDown() {
        appDatabase.close()
    }

    @Test
    fun whenMigrationRunsThenAllFormFactorFavoriteFoldersCreated() {
        whenMigrationApplied()

        val allFolders = appDatabase.syncEntitiesDao().allEntitiesByTypeSync(EntityType.FOLDER)

        Assert.assertNotNull(allFolders.find { it.entityId == SavedSitesNames.FAVORITES_ROOT })
        Assert.assertNotNull(allFolders.find { it.entityId == SavedSitesNames.FAVORITES_MOBILE_ROOT })
        Assert.assertNotNull(allFolders.find { it.entityId == SavedSitesNames.FAVORITES_DESKTOP_ROOT })
    }

    @Test
    fun whenFavoritesExistThenMigrationIsSuccessful() = runTest {
        val totalFavorites = 10
        givenSomeFavorites(totalFavorites)
        whenMigrationApplied()

        val relations = savedSitesRelationsDao.relations()
        assertEquals(relations.size, totalFavorites * DEVICE_ROOT_FOLDERS)

        val entities = savedSitesEntitiesDao.entities()
        assertEquals(entities.size, totalFavorites + ROOT_FOLDERS)
    }

    @Test
    fun whenBookmarksWithoutFoldersExistThenMigrationIsSuccessful() = runTest {
        val totalBookmarks = 10

        givenSomeBookmarks(totalBookmarks, SavedSitesNames.BOOMARKS_ROOT_ID)

        whenMigrationApplied()

        assertTrue(savedSitesEntitiesDao.hasEntities())
        assertTrue(savedSitesRelationsDao.hasRelations())

        savedSitesEntitiesDao.entitiesByType(BOOKMARK).test {
            val entities = awaitItem()
            assertTrue(entities.size == totalBookmarks)
        }

        val relations = (savedSitesRelationsDao.relations())
        assertTrue(relations.size == totalBookmarks)
    }

    @Test
    fun whenBookmarksWithFoldersExistThenMigrationIsSuccessful() = runTest {
        val totalFolder = 10
        val bookmarksPerFolder = 5
        createFoldersTree(totalFolder, bookmarksPerFolder)

        val totalFavorites = 15
        givenSomeFavorites(totalFavorites)

        whenMigrationApplied()

        assertTrue(savedSitesEntitiesDao.hasEntities())
        assertTrue(savedSitesRelationsDao.hasRelations())

        val entities = savedSitesEntitiesDao.entities()
        assertTrue(entities.size == totalFolder + (totalFolder * bookmarksPerFolder) + totalFavorites + ROOT_FOLDERS)

        val relations = (savedSitesRelationsDao.relations())
        assertTrue(relations.size == totalFolder + (totalFolder * bookmarksPerFolder) + (totalFavorites * DEVICE_ROOT_FOLDERS))
    }

    @Test
    fun whenBookmarksWithFoldersAndFavoritesExistThenMigrationIsSuccessful() = runTest {
        val totalFolder = 10
        val bookmarksPerFolder = 5
        createFoldersTree(totalFolder, bookmarksPerFolder)

        assertEquals(10, bookmarkFoldersDao.getBookmarkFoldersSync().size)
        assertEquals(50, bookmarksDao.getBookmarks().first().size)

        whenMigrationApplied()

        assertTrue(savedSitesEntitiesDao.hasEntities())
        assertTrue(savedSitesRelationsDao.hasRelations())

        val entities = savedSitesEntitiesDao.entities()
        assertEquals(entities.size, totalFolder + (totalFolder * bookmarksPerFolder) + ROOT_FOLDERS)

        val relations = (savedSitesRelationsDao.relations())
        assertEquals(relations.size, totalFolder + (totalFolder * bookmarksPerFolder))
    }

    @Test
    fun whenBookmarkAndFavoriteHaveSameUrlThenBookmarkAlsoMigratedAsFavorite() {
        bookmarksDao.insert(BookmarkEntity(1, "bookmark1", "http://test.com", 0))
        favoritesDao.insert(FavoriteEntity(2, "favorite1", "http://test.com", 0))

        whenMigrationApplied()

        // only one entity migrated
        assertTrue(savedSitesEntitiesDao.entities().size == 1 + ROOT_FOLDERS)

        // relations migrated, one for bookmarks and another for favorites
        assertTrue(savedSitesRelationsDao.relations().size == 1 * DEVICE_ROOT_FOLDERS)

        val bookmarks = savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.BOOKMARKS_ROOT)
        val favorites = savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT)

        assertTrue(bookmarks.size == 1)
        assertTrue(bookmarks.first().title == "bookmark1")

        assertTrue(favorites.size == 1)
        assertTrue(favorites.first().title == "bookmark1")
    }

    @Test
    fun whenBookmarkAndFavoriteHaveDifferentUrlThenBothAreMigrated() {
        bookmarksDao.insert(BookmarkEntity(1, "Bookmark1", "http://test.com", 0))
        favoritesDao.insert(FavoriteEntity(2, "Favorite1", "http://testee.com", 0))

        whenMigrationApplied()

        val bookmarksMigrated = 2
        val favoritesMigrated = 1
        assertEquals(savedSitesEntitiesDao.entities().size, ROOT_FOLDERS + bookmarksMigrated)
        assertEquals(
            savedSitesRelationsDao.relations().size,
            (bookmarksMigrated * BOOKMARK_ROOT_FOLDERS) + (favoritesMigrated * FAVORITES_DEVICE_ROOT_FOLDERS),
        )

        val bookmarks = savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.BOOKMARKS_ROOT)
        val favorites = savedSitesEntitiesDao.entitiesInFolderSync(SavedSitesNames.FAVORITES_ROOT)

        assertTrue(bookmarks.size == 2)
        assertTrue(bookmarks[0].title == "Bookmark1")
        assertTrue(bookmarks[1].title == "Favorite1")

        assertTrue(favorites.size == 1)
        assertTrue(favorites.first().title == "Favorite1")
    }

    @Test
    fun whenDataIsMigratedThenOldTablesAreDeleted() {
        givenSomeFavorites(10)
        givenSomeBookmarks(5, SavedSitesNames.BOOMARKS_ROOT_ID)
        whenMigrationApplied()

        assertFalse(favoritesDao.userHasFavorites())
        assertFalse(bookmarksDao.bookmarksCount() > 0)
        assertTrue(bookmarkFoldersDao.getBookmarkFoldersSync().isEmpty())
    }

    @Test
    fun whenNeedsFormFactorMigrationThenFavoritesAreCopiedIntoFormFactorFavoriteFolder() {
        givenSomeFavoritesSavedSites(10)
        whenMigrationApplied()

        assertEquals(savedSitesEntitiesDao.entities().size, ROOT_FOLDERS + 10)
        assertEquals(
            savedSitesRelationsDao.relations().size,
            (10 * BOOKMARK_ROOT_FOLDERS) + (10 * FAVORITES_DEVICE_ROOT_FOLDERS),
        )
    }

    @Test
    fun whenNeedsFormFactorMigrationThenFormFactorFolderLastModifiedUdpated() {
        givenSomeFavoritesSavedSites(10)
        whenMigrationApplied()

        val mobileLastModified = savedSitesEntitiesDao.entityById(SavedSitesNames.FAVORITES_MOBILE_ROOT)!!.lastModified
        assertTrue(mobileLastModified.isNullOrEmpty().not())
    }

    @Test
    fun whenAnyFavoriteRootFolderHasRelationWithBookmarksRootThenRemoveRelation() = runTest {
        givenFormFactorFolderExist()
        givenSomeFavoritesSavedSitesIn(
            total = 10,
            folderIds = arrayOf(SavedSitesNames.FAVORITES_ROOT, SavedSitesNames.FAVORITES_MOBILE_ROOT),
        )
        savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.BOOKMARKS_ROOT, entityId = SavedSitesNames.FAVORITES_ROOT))
        savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.BOOKMARKS_ROOT, entityId = SavedSitesNames.FAVORITES_MOBILE_ROOT))
        savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.BOOKMARKS_ROOT, entityId = SavedSitesNames.FAVORITES_DESKTOP_ROOT))

        whenMigrationApplied()

        val bookmarkRootRelations = savedSitesRelationsDao.relations(SavedSitesNames.BOOKMARKS_ROOT).first()

        assertTrue(bookmarkRootRelations.size == 10)
    }

    private fun whenMigrationApplied() {
        appDatabase.apply {
            whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
            AppDatabaseBookmarksMigrationCallback({ this }, coroutineRule.testDispatcherProvider, appBuildConfig).runMigration()
        }
    }

    private fun givenSomeFavorites(total: Int) {
        val favorites = mutableListOf<FavoriteEntity>()
        for (index in 1..total) {
            favorites.add(FavoriteEntity(index.toLong(), "Favorite$index", "http://favexample$index.com", index))
        }
        favoritesDao.insertList(favorites)
    }

    private fun givenSomeBookmarks(
        total: Int,
        bookmarkFolderId: Long,
    ) {
        val bookmarks = mutableListOf<BookmarkEntity>()
        for (index in 1..total) {
            bookmarks.add(BookmarkEntity("$index$bookmarkFolderId".toLong(), "Bookmark$index", "http://bookmark$index.com", bookmarkFolderId))
        }
        bookmarksDao.insertList(bookmarks)
    }

    private fun givenAFolder(index: Int) {
        val folderEntity = if (index == SavedSitesNames.BOOMARKS_ROOT_ID.toInt()) {
            BookmarkFolderEntity(SavedSitesNames.BOOMARKS_ROOT_ID, "folder$index", SavedSitesNames.BOOMARKS_ROOT_ID)
        } else {
            BookmarkFolderEntity(index.toLong(), "folder$index", (index - 1).toLong())
        }

        bookmarkFoldersDao.insert(folderEntity)
    }

    private fun createFoldersTree(
        totalFolders: Int,
        bookmarksPerFolder: Int,
    ) {
        for (index in 1..totalFolders) {
            givenAFolder(index)
            givenSomeBookmarks(bookmarksPerFolder, index.toLong())
        }
    }

    private fun givenSomeFavoritesSavedSites(
        total: Int,
    ) {
        for (index in 1..total) {
            val favorite = Entity(
                UUID.randomUUID().toString(),
                "Favorite$index",
                "http://favexample$index.com",
                EntityType.BOOKMARK,
            )
            savedSitesEntitiesDao.insert(favorite)
            savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.FAVORITES_ROOT, entityId = favorite.entityId))
            savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.BOOKMARKS_ROOT, entityId = favorite.entityId))
        }
    }

    private fun givenSomeFavoritesSavedSitesIn(
        total: Int,
        vararg folderIds: String,
    ) {
        for (index in 1..total) {
            val favorite = Entity(
                UUID.randomUUID().toString(),
                "Favorite$index",
                "http://favexample$index.com",
                EntityType.BOOKMARK,
            )
            savedSitesEntitiesDao.insert(favorite)
            folderIds.forEach {
                savedSitesRelationsDao.insert(Relation(folderId = it, entityId = favorite.entityId))
            }
            savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.BOOKMARKS_ROOT, entityId = favorite.entityId))
        }
    }

    private fun givenFormFactorFolderExist() {
        savedSitesEntitiesDao.insert(Entity(SavedSitesNames.FAVORITES_DESKTOP_ROOT, SavedSitesNames.FAVORITES_DESKTOP_NAME, "", EntityType.FOLDER))
        savedSitesEntitiesDao.insert(Entity(SavedSitesNames.FAVORITES_MOBILE_ROOT, SavedSitesNames.FAVORITES_MOBILE_NAME, "", EntityType.FOLDER))
        savedSitesEntitiesDao.insert(Entity(SavedSitesNames.FAVORITES_ROOT, SavedSitesNames.FAVORITES_NAME, "", EntityType.FOLDER))
    }

    companion object {
        const val BOOKMARK_ROOT_FOLDERS = 1
        const val FAVORITES_DEVICE_ROOT_FOLDERS = 2
        const val DEVICE_ROOT_FOLDERS = FAVORITES_DEVICE_ROOT_FOLDERS + BOOKMARK_ROOT_FOLDERS
        const val FAVORITES_ROOT_FOLDERS = 3
        const val ROOT_FOLDERS = FAVORITES_ROOT_FOLDERS + BOOKMARK_ROOT_FOLDERS
    }
}
