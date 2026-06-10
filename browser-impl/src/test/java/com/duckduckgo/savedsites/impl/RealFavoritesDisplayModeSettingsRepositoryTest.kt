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

package com.duckduckgo.savedsites.impl

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.sync.DisplayModeSyncableSetting
import com.duckduckgo.savedsites.store.FavoritesDisplayMode
import com.duckduckgo.savedsites.store.FavoritesDisplayMode.NATIVE
import com.duckduckgo.savedsites.store.FavoritesDisplayMode.UNIFIED
import com.duckduckgo.savedsites.store.Relation
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import com.duckduckgo.savedsites.store.SavedSitesSettingsStore
import com.duckduckgo.sync.api.SyncState
import com.duckduckgo.sync.api.SyncStateMonitor
import com.duckduckgo.sync.settings.api.SyncSettingsListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class RealFavoritesDisplayModeSettingsRepositoryTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    private var db: AppDatabase = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private var savedSitesRelationsDao: SavedSitesRelationsDao = db.syncRelationsDao()

    private val savedSitesSettingsStore = FakeSavedSitesSettingsStore(coroutineRule.testScope)
    private val syncSettingsListener: SyncSettingsListener = mock()
    private val syncStateFlow = MutableStateFlow(SyncState.READY)
    private val syncStateMonitor: SyncStateMonitor = FakeSyncStateMonitor(syncStateFlow)
    private val syncableSetting: DisplayModeSyncableSetting = DisplayModeSyncableSetting(
        savedSitesSettingsStore,
        syncSettingsListener,
    )
    private val testee = RealFavoritesDisplayModeSettingsRepository(
        savedSitesSettingsStore,
        syncableSetting,
        syncStateMonitor,
        savedSitesRelationsDao,
        coroutineRule.testScope,
        coroutineRule.testDispatcherProvider,
    )

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenObserverAddedThenCurrentViewStateEmitted() = runTest {
        testee.favoritesDisplayModeFlow().test {
            assertEquals(NATIVE, awaitItem())
        }
    }

    @Test
    fun whenDisplayModeChangedThenViewStateIsUpdated() = runTest {
        testee.favoritesDisplayModeFlow().test {
            awaitItem()
            testee.favoritesDisplayMode = UNIFIED
            assertEquals(UNIFIED, awaitItem())
        }
    }

    @Test
    fun whenDisplayModeChangedThenNotifySyncableSetting() = runTest {
        testee.favoritesDisplayMode = UNIFIED

        verify(syncSettingsListener).onSettingChanged(syncableSetting.key)
    }

    @Test
    fun whenSyncDisabledThenQueryFolderIsFavoritesRoot() = runTest {
        syncStateFlow.value = SyncState.OFF
        assertEquals(SavedSitesNames.FAVORITES_ROOT, testee.getQueryFolder())
    }

    @Test
    fun whenUserEnabledSyncThenEmitNewQueryFolder() = runTest {
        syncStateFlow.value = SyncState.OFF
        testee.getFavoriteFolderFlow().test {
            assertEquals(SavedSitesNames.FAVORITES_ROOT, awaitItem())
            syncStateFlow.value = SyncState.READY
            assertEquals(SavedSitesNames.FAVORITES_MOBILE_ROOT, awaitItem())
            savedSitesSettingsStore.favoritesDisplayMode = UNIFIED
            assertEquals(SavedSitesNames.FAVORITES_ROOT, awaitItem())
        }
    }

    @Test
    fun whenSyncEnabledAndNativeModeThenQueryFolderIsMobileRoot() = runTest {
        syncStateFlow.value = SyncState.READY
        savedSitesSettingsStore.favoritesDisplayMode = NATIVE
        assertEquals(SavedSitesNames.FAVORITES_MOBILE_ROOT, testee.getQueryFolder())
    }

    @Test
    fun whenSyncEnabledAndUnifiedModeThenQueryFolderIsMobileRoot() = runTest {
        syncStateFlow.value = SyncState.READY
        savedSitesSettingsStore.favoritesDisplayMode = UNIFIED
        assertEquals(SavedSitesNames.FAVORITES_ROOT, testee.getQueryFolder())
    }

    @Test
    fun whenSyncDisabledThenInsertFolderIsFavoritesRoot() = runTest {
        syncStateFlow.value = SyncState.OFF
        assertEquals(listOf(SavedSitesNames.FAVORITES_ROOT), testee.getInsertFolder())
    }

    @Test
    fun whenSyncEnabledAndNativeModeThenInsertFolderIsMobileAndFavoritesRoot() = runTest {
        syncStateFlow.value = SyncState.READY
        savedSitesSettingsStore.favoritesDisplayMode = NATIVE
        assertTrue(testee.getInsertFolder().size == 2)
        assertTrue(testee.getInsertFolder().contains(SavedSitesNames.FAVORITES_ROOT))
        assertTrue(testee.getInsertFolder().contains(SavedSitesNames.FAVORITES_MOBILE_ROOT))
    }

    @Test
    fun whenSyncEnabledAndUnifiedModeThenInsertFolderIsMobileAndFavoritesRoot() = runTest {
        syncStateFlow.value = SyncState.READY
        savedSitesSettingsStore.favoritesDisplayMode = UNIFIED
        assertTrue(testee.getInsertFolder().size == 2)
        assertTrue(testee.getInsertFolder().contains(SavedSitesNames.FAVORITES_ROOT))
        assertTrue(testee.getInsertFolder().contains(SavedSitesNames.FAVORITES_MOBILE_ROOT))
    }

    @Test
    fun whenSyncDisabledThenDeleteFolderIsFavoritesRoot() = runTest {
        syncStateFlow.value = SyncState.OFF
        assertEquals(listOf(SavedSitesNames.FAVORITES_ROOT), testee.getDeleteFolder("entityId"))
    }

    @Test
    fun whenSyncEnabledAndNativeModeThenDeleteFolderIsMobileAndFavoritesRoot() = runTest {
        syncStateFlow.value = SyncState.READY
        savedSitesSettingsStore.favoritesDisplayMode = NATIVE
        assertTrue(testee.getDeleteFolder("entityId").size == 2)
        assertTrue(testee.getDeleteFolder("entityId").contains(SavedSitesNames.FAVORITES_ROOT))
        assertTrue(testee.getDeleteFolder("entityId").contains(SavedSitesNames.FAVORITES_MOBILE_ROOT))
    }

    @Test
    fun whenSyncEnabledAndNativeModeAndFavoriteIsDesktopThenDeleteFolderIsMobile() = runTest {
        savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.FAVORITES_DESKTOP_ROOT, entityId = "entityId"))
        syncStateFlow.value = SyncState.READY
        savedSitesSettingsStore.favoritesDisplayMode = NATIVE

        assertTrue(testee.getDeleteFolder("entityId").size == 1)
        assertTrue(testee.getDeleteFolder("entityId").contains(SavedSitesNames.FAVORITES_MOBILE_ROOT))
    }

    @Test
    fun whenSyncEnabledAndUnifiedModeThenDeleteFolderIsAllFolders() = runTest {
        syncStateFlow.value = SyncState.READY
        savedSitesSettingsStore.favoritesDisplayMode = UNIFIED
        assertTrue(testee.getDeleteFolder("entityId").size == 3)
        assertTrue(testee.getDeleteFolder("entityId").contains(SavedSitesNames.FAVORITES_ROOT))
        assertTrue(testee.getDeleteFolder("entityId").contains(SavedSitesNames.FAVORITES_MOBILE_ROOT))
        assertTrue(testee.getDeleteFolder("entityId").contains(SavedSitesNames.FAVORITES_DESKTOP_ROOT))
    }
}

class FakeSyncStateMonitor(private val syncStateFlow: Flow<SyncState>) : SyncStateMonitor {
    override fun syncState(): Flow<SyncState> = syncStateFlow
}

class FakeSavedSitesSettingsStore(
    private val coroutineScope: CoroutineScope,
) : SavedSitesSettingsStore {
    val flow = MutableStateFlow(NATIVE)
    override var favoritesDisplayMode: FavoritesDisplayMode
        get() = flow.value
        set(value) {
            coroutineScope.launch {
                flow.emit(value)
            }
        }
    override fun favoritesFormFactorModeFlow(): Flow<FavoritesDisplayMode> = flow
}
