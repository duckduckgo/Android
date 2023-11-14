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

package com.duckduckgo.app.sync

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.FavoritesDisplayModeSettingsRepository
import com.duckduckgo.savedsites.impl.sync.RealSavedSitesFormFactorSyncMigration
import com.duckduckgo.savedsites.store.Entity
import com.duckduckgo.savedsites.store.EntityType.BOOKMARK
import com.duckduckgo.savedsites.store.EntityType.FOLDER
import com.duckduckgo.savedsites.store.FavoritesDisplayMode
import com.duckduckgo.savedsites.store.Relation
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealSavedSitesFormFactorSyncMigrationTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule var coroutineRule = CoroutineTestRule()

    private val savedSiteSettingsRepository: FavoritesDisplayModeSettingsRepository = FakeFavoritesDisplayModeSettingsRepository()
    private lateinit var db: AppDatabase
    private lateinit var savedSitesEntitiesDao: SavedSitesEntitiesDao
    private lateinit var savedSitesRelationsDao: SavedSitesRelationsDao

    private lateinit var testee: RealSavedSitesFormFactorSyncMigration

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        savedSitesEntitiesDao = db.syncEntitiesDao()
        savedSitesRelationsDao = db.syncRelationsDao()

        testee = RealSavedSitesFormFactorSyncMigration(
            savedSitesEntitiesDao,
            savedSitesRelationsDao,
            savedSiteSettingsRepository,
        )
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenSyncEnabledThenFormFactorFavoriteFoldersCreated() {
        testee.onFormFactorFavouritesEnabled()

        assertNotNull(savedSitesEntitiesDao.entityById(SavedSitesNames.FAVORITES_ROOT))
        assertNotNull(savedSitesEntitiesDao.entityById(SavedSitesNames.FAVORITES_MOBILE_ROOT))
        assertNotNull(savedSitesEntitiesDao.entityById(SavedSitesNames.FAVORITES_DESKTOP_ROOT))
    }

    @Test
    fun whenSyncEnabledThenAllFavoritesCopiedToNativeFolder() = runTest {
        val entities = givenEntitiesWithIds("Entity1", "Entity2", "Entity3")
        givenEntitiesStoredIn(entities, SavedSitesNames.FAVORITES_ROOT)

        testee.onFormFactorFavouritesEnabled()

        val rootRelations = savedSitesRelationsDao.relations(SavedSitesNames.FAVORITES_ROOT).first()
        val nativeRelations = savedSitesRelationsDao.relations(SavedSitesNames.FAVORITES_MOBILE_ROOT).first()

        assertNotEquals(rootRelations, nativeRelations)
        assertEquals(rootRelations.map { it.entityId }, nativeRelations.map { it.entityId })
    }

    @Test
    fun whenDisplayModeNativeAndFavoritesInAllFormFactorFolderThenCopyNativeFavoritesInRoot() = runTest {
        val desktopEntities = givenEntitiesWithIds("Entity1", "Entity2", "Entity3")
        givenEntitiesStoredIn(desktopEntities, SavedSitesNames.FAVORITES_DESKTOP_ROOT)
        val nativeEntities = givenEntitiesWithIds("Entity4", "Entity5", "Entity6")
        givenEntitiesStoredIn(nativeEntities, SavedSitesNames.FAVORITES_MOBILE_ROOT)
        givenEntitiesStoredIn(nativeEntities + desktopEntities, SavedSitesNames.FAVORITES_ROOT)
        savedSiteSettingsRepository.favoritesDisplayMode = FavoritesDisplayMode.NATIVE

        testee.onFormFactorFavouritesDisabled()

        val desktopRelations = savedSitesRelationsDao.relations(SavedSitesNames.FAVORITES_DESKTOP_ROOT).first()
        val nativeRelations = savedSitesRelationsDao.relations(SavedSitesNames.FAVORITES_MOBILE_ROOT).first()
        val rootRelations = savedSitesRelationsDao.relations(SavedSitesNames.FAVORITES_ROOT).first()

        assertEquals(nativeEntities.map { it.entityId }, rootRelations.map { it.entityId })
        assertEquals(emptyList<String>(), nativeRelations.map { it.entityId })
        assertEquals(emptyList<String>(), desktopRelations.map { it.entityId })
    }

    @Test
    fun whenDisplayModeUnifiedAndFavoritesInAllFormFactorFolderThenRemoveContentFromFormFactorFolders() = runTest {
        val desktopEntities = givenEntitiesWithIds("Entity1", "Entity2", "Entity3")
        givenEntitiesStoredIn(desktopEntities, SavedSitesNames.FAVORITES_DESKTOP_ROOT)
        val nativeEntities = givenEntitiesWithIds("Entity4", "Entity5", "Entity6")
        givenEntitiesStoredIn(nativeEntities, SavedSitesNames.FAVORITES_MOBILE_ROOT)
        givenEntitiesStoredIn(nativeEntities + desktopEntities, SavedSitesNames.FAVORITES_ROOT)
        savedSiteSettingsRepository.favoritesDisplayMode = FavoritesDisplayMode.UNIFIED

        testee.onFormFactorFavouritesDisabled()

        val desktopRelations = savedSitesRelationsDao.relations(SavedSitesNames.FAVORITES_DESKTOP_ROOT).first()
        val nativeRelations = savedSitesRelationsDao.relations(SavedSitesNames.FAVORITES_MOBILE_ROOT).first()
        val rootRelations = savedSitesRelationsDao.relations(SavedSitesNames.FAVORITES_ROOT).first()

        assertEquals(rootRelations.map { it.entityId }, rootRelations.map { it.entityId })
        assertEquals(emptyList<String>(), nativeRelations.map { it.entityId })
        assertEquals(emptyList<String>(), desktopRelations.map { it.entityId })
    }

    @Test
    fun whenSyncDisabledAndDisplayModeUnifiedThenNewDisplayModeIsNative() {
        savedSiteSettingsRepository.favoritesDisplayMode = FavoritesDisplayMode.UNIFIED

        testee.onFormFactorFavouritesDisabled()

        assertEquals(FavoritesDisplayMode.NATIVE, savedSiteSettingsRepository.favoritesDisplayMode)
    }

    @Test
    fun whenSyncDisabledAndDisplayModeNativeThenNewDisplayModeIsNative() {
        savedSiteSettingsRepository.favoritesDisplayMode = FavoritesDisplayMode.NATIVE

        testee.onFormFactorFavouritesDisabled()

        assertEquals(FavoritesDisplayMode.NATIVE, savedSiteSettingsRepository.favoritesDisplayMode)
    }

    @Test
    fun whenSyncDisabledThenOnlyFavoritesRootFolderExist() {
        savedSitesEntitiesDao.insert(
            Entity(entityId = SavedSitesNames.FAVORITES_ROOT, url = "", title = SavedSitesNames.FAVORITES_NAME, type = FOLDER),
        )
        savedSitesEntitiesDao.insert(
            Entity(entityId = SavedSitesNames.FAVORITES_MOBILE_ROOT, url = "", title = SavedSitesNames.FAVORITES_MOBILE_NAME, type = FOLDER),
        )
        savedSitesEntitiesDao.insert(
            Entity(entityId = SavedSitesNames.FAVORITES_DESKTOP_ROOT, url = "", title = SavedSitesNames.FAVORITES_DESKTOP_NAME, type = FOLDER),
        )

        testee.onFormFactorFavouritesDisabled()

        assertNotNull(savedSitesEntitiesDao.entityById(SavedSitesNames.FAVORITES_ROOT))
        assertNull(savedSitesEntitiesDao.entityById(SavedSitesNames.FAVORITES_MOBILE_ROOT))
        assertNull(savedSitesEntitiesDao.entityById(SavedSitesNames.FAVORITES_DESKTOP_ROOT))
    }

    private fun givenEntitiesWithIds(
        vararg ids: String,
    ): List<Entity> {
        val list = mutableListOf<Entity>()
        ids.forEach {
            list.add(Entity(entityId = it, title = it, url = "http://example.com", type = BOOKMARK))
        }
        return list
    }

    private fun givenEntitiesStoredIn(
        entities: List<Entity>,
        folderId: String,
    ) {
        entities.forEach {
            savedSitesEntitiesDao.insert(it)
            savedSitesRelationsDao.insert(Relation(folderId = folderId, entityId = it.entityId))
        }
    }
}
