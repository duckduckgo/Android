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

package com.duckduckgo.sync.settings.impl

import androidx.arch.core.executor.testing.*
import androidx.room.*
import androidx.test.ext.junit.runners.*
import androidx.test.platform.app.*
import com.duckduckgo.app.*
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.api.*
import com.duckduckgo.sync.api.engine.*
import com.duckduckgo.sync.api.engine.ModifiedSince.FirstSync
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.runner.*

@RunWith(AndroidJUnit4::class)
class SettingsSyncDataProviderTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, SettingsDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    val metadataDao = db.settingsSyncDao()
    val settingSyncStore = FakeSettingsSyncStore()
    val duckAddressSetting = FakeSyncableSetting()
    val syncableSettingsPP = SyncableSettingsPluginPoint(mutableListOf(duckAddressSetting))

    private val testee: SettingsSyncDataProvider = SettingsSyncDataProvider(
        syncableSettings = syncableSettingsPP,
        settingsSyncMetadataDao = metadataDao,
        settingsSyncStore = settingSyncStore,
        syncCrypto = FakeCrypto(),
    )

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenGetChangesForFirstTimeThenMetadataIsInitialized() = runTest {
        assertTrue(metadataDao.getAllObservable().first().isEmpty())

        testee.getChanges()

        assertNotNull(metadataDao.getAllObservable().first().find { it.key == duckAddressSetting.key })
    }

    @Test
    fun whenGetChangesForFirstTimeThenExistingMetadataUpdated() = runTest {
        metadataDao.addOrUpdate(
            SettingsSyncMetadataEntity(duckAddressSetting.key, "", ""),
        )
        syncableSettingsPP.syncableSettings.add(
            FakeSyncableSetting().apply {
                key = "new_setting"
            },
        )
        assertTrue(metadataDao.getAllObservable().first().size == 1)

        testee.getChanges()

        assertTrue(metadataDao.getAllObservable().first().size == 2)
        val duckAddressMetadata = metadataDao.getAllObservable().first().find { it.key == duckAddressSetting.key }
        assertTrue(duckAddressMetadata?.modified_at.isNullOrEmpty().not())
    }

    @Test
    fun whenGetChangesForFirstSyncThenChangesIncludeAllValues() {
        val changes = testee.getChanges()

        assertTrue(changes.type == SyncableType.SETTINGS)
        patchAdapter.fromJson(changes.jsonString)?.let { patch ->
            assertEquals(1, patch.settings.updates.size)
            assertEquals(duckAddressSetting.key, patch.settings.updates[0].key)
            assertEquals(duckAddressSetting.getValue(), patch.settings.updates[0].value)
            assertTrue(patch.settings.updates[0].deleted.isNullOrEmpty())
        }
        assertTrue(changes.isFirstSync())
    }

    @Test
    @Ignore("Need to decide strategy first")
    fun whenGetChangesSubsequentCallsWithNewValueThenIncludeNewValues() {
        settingSyncStore.serverModifiedSince = "2022-01-01T00:00:00Z"
        settingSyncStore.clientModifiedSince = "2022-01-01T00:00:00Z"

        val changes = testee.getChanges()

        assertTrue(changes.type == SyncableType.SETTINGS)
        patchAdapter.fromJson(changes.jsonString)?.let { patch ->
            assertEquals(1, patch.settings.updates.size)
            assertEquals(duckAddressSetting.key, patch.settings.updates[0].key)
            assertEquals(duckAddressSetting.getValue(), patch.settings.updates[0].value)
            assertTrue(patch.settings.updates[0].deleted.isNullOrEmpty())
        }
        assertTrue(changes.modifiedSince is ModifiedSince.Timestamp)
    }

    @Test
    fun whenGetChangesSubsequentCallsAndNoChangesThenUpdatesAreEmpty() {
        settingSyncStore.serverModifiedSince = "2022-01-01T00:00:00Z"
        settingSyncStore.clientModifiedSince = "2022-01-01T00:00:00Z"
        metadataDao.addOrUpdate(SettingsSyncMetadataEntity(duckAddressSetting.key, "2022-01-01T00:00:00Z", ""))

        val changes = testee.getChanges()

        assertTrue(changes.type == SyncableType.SETTINGS)
        assertTrue(changes.jsonString.isEmpty())
        assertTrue(changes.modifiedSince is ModifiedSince.Timestamp)
    }

    @Test
    fun whenDBHasDataButItIsFirstSyncThenIncludeAllValues() {
        metadataDao.addOrUpdate(SettingsSyncMetadataEntity(duckAddressSetting.key, "2022-01-01T00:00:00Z", ""))

        val changes = testee.getChanges()

        assertTrue(changes.type == SyncableType.SETTINGS)
        patchAdapter.fromJson(changes.jsonString)?.let { patch ->
            assertEquals(1, patch.settings.updates.size)
            assertEquals(duckAddressSetting.key, patch.settings.updates[0].key)
            assertEquals(duckAddressSetting.getValue(), patch.settings.updates[0].value)
            assertTrue(patch.settings.updates[0].deleted.isNullOrEmpty())
        }
        assertTrue(changes.modifiedSince is FirstSync)
    }

    @Test
    fun whenGetChangesForFirstSyncAndSettingNullThenSendAsDeleted() {
        duckAddressSetting.save(null)

        val changes = testee.getChanges()

        assertTrue(changes.type == SyncableType.SETTINGS)
        patchAdapter.fromJson(changes.jsonString)?.let { patch ->
            assertEquals(1, patch.settings.updates.size)
            assertEquals(duckAddressSetting.key, patch.settings.updates[0].key)
            assertEquals("", patch.settings.updates[0].value)
            assertFalse(patch.settings.updates[0].deleted.isNullOrEmpty())
        }
        assertTrue(changes.modifiedSince is FirstSync)
    }

    @Test
    fun whenGetChangesSubsequentCallsAndSettingNullThenSendAsDeleted() {
        settingSyncStore.serverModifiedSince = "2022-01-01T00:00:00Z"
        settingSyncStore.clientModifiedSince = "2022-01-01T00:00:00Z"
        metadataDao.addOrUpdate(SettingsSyncMetadataEntity(duckAddressSetting.key, "2022-01-02T00:00:00Z", ""))
        duckAddressSetting.save(null)

        val changes = testee.getChanges()

        assertTrue(changes.type == SyncableType.SETTINGS)
        patchAdapter.fromJson(changes.jsonString)?.let { patch ->
            assertEquals(1, patch.settings.updates.size)
            assertEquals(duckAddressSetting.key, patch.settings.updates[0].key)
            assertEquals("", patch.settings.updates[0].value)
            assertFalse(patch.settings.updates[0].deleted.isNullOrEmpty())
        }
        assertTrue(changes.modifiedSince is ModifiedSince.Timestamp)
    }

    @Test
    fun whenSyncableSettingNotFoundThenSkipUpdate() {
        settingSyncStore.serverModifiedSince = "2022-01-01T00:00:00Z"
        settingSyncStore.clientModifiedSince = "2022-01-01T00:00:00Z"
        metadataDao.addOrUpdate(SettingsSyncMetadataEntity("unknown_setting", "2022-01-02T00:00:00Z", ""))

        val changes = testee.getChanges()

        assertTrue(changes.jsonString.isEmpty())
    }

    companion object {
        private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val patchAdapter: JsonAdapter<SyncSettingsRequest> = moshi.adapter(SyncSettingsRequest::class.java).serializeNulls()
    }
}
