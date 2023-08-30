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

package com.duckduckgo.settings.impl

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.*
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.settings.api.SyncableSetting
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.SyncChangesResponse
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.DEDUPLICATION
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.TIMESTAMP
import com.duckduckgo.sync.api.engine.SyncableType
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.*
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.timeout

@RunWith(AndroidJUnit4::class)
class SettingsSyncDataPersisterTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, SettingsDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    val metadataDao = db.settingsSyncDao()

    val duckAddress = spy(FakeDuckAddressSyncableSetting())

    val settingSyncStore = FakeSettingsSyncStore()

    private val testee = SettingsSyncDataPersister(
        duckAddress = duckAddress,
        settingsSyncMetadataDao = metadataDao,
        syncSettingsSyncStore = settingSyncStore,
    )

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenPersistChangesDeduplicationWithdValueThenCallMergeWithValue() {
        val result = testee.persist(
            changes = SyncChangesResponse(
                type = SyncableType.SETTINGS,
                jsonString = responseWithValuesObject,
            ),
            conflictResolution = DEDUPLICATION,
        )

        assertTrue(result is Success)
        verify(duckAddress).mergeRemote("fake_value")
    }

    @Test
    fun whenPersistChangesDeduplicationWithDeletedValueThenCallMergeWithNull() {
        val result = testee.persist(
            changes = SyncChangesResponse(
                type = SyncableType.SETTINGS,
                jsonString = responseWithDeletedObject,
            ),
            conflictResolution = DEDUPLICATION,
        )

        assertTrue(result is Success)
        verify(duckAddress).mergeRemote(null)
    }

    @Test
    fun whenPersistChangesTimestampAndNoRecentChangeThenCallMergeWithValue() {
        settingSyncStore.startTimeStamp = "2023-08-31T10:06:16.022Z"
        val result = testee.persist(
            changes = SyncChangesResponse(
                type = SyncableType.SETTINGS,
                jsonString = responseWithValuesObject,
            ),
            conflictResolution = TIMESTAMP,
        )

        assertTrue(result is Success)
        verify(duckAddress).save("fake_value")
    }

    @Test
    fun whenPersistChangesTimestampWithDeletedValueThenCallSaveWithNull() {
        val result = testee.persist(
            changes = SyncChangesResponse(
                type = SyncableType.SETTINGS,
                jsonString = responseWithDeletedObject,
            ),
            conflictResolution = TIMESTAMP,
        )

        assertTrue(result is Success)
        verify(duckAddress).save(null)
    }

    @Test
    fun whenPersistChangesTimestampButRecentlyModifiedThenSkip() {
        settingSyncStore.startTimeStamp = "2023-08-31T10:06:16.022Z"
        metadataDao.addOrUpdate(
            SettingsSyncMetadataEntity(
                key = "fake_setting",
                modified_at = "2023-08-31T10:06:17.022Z",
                deleted_at = null,
            )
        )

        val result = testee.persist(
            changes = SyncChangesResponse(
                type = SyncableType.SETTINGS,
                jsonString = responseWithValuesObject,
            ),
            conflictResolution = TIMESTAMP,
        )

        assertTrue(result is Success)
        verify(duckAddress, times(0)).save("fake_value")
    }

    @Test
    fun whenPersistChangesSucceedsThenUpdateServerAndClientTimestamps() {
        settingSyncStore.startTimeStamp = "2023-08-31T10:06:16.022Z"

        val result = testee.persist(
            changes = SyncChangesResponse(
                type = SyncableType.SETTINGS,
                jsonString = responseWithValuesObject,
            ),
            conflictResolution = DEDUPLICATION,
        )

        assertTrue(result is Success)
        assertEquals("2023-08-31T10:06:16.022Z", settingSyncStore.serverModifiedSince)
        assertEquals("2023-08-31T10:06:16.022Z", settingSyncStore.clientModifiedSince)
    }

    companion object {
        val responseWithDeletedObject = "{\"settings\":{\"entries\":[{\"key\":\"fake_setting\",\"value\":\"\",\"deleted\":\"2023-08-31T10:06:16.022Z\",\"last_modified\":\"2023-08-31T10:06:16.022Z\"}],\"last_modified\":\"2023-08-31T10:06:16.022Z\"}}"
        val responseWithValuesObject = "{\"settings\":{\"entries\":[{\"key\":\"fake_setting\",\"value\":\"fake_value\",\"deleted\":\"\",\"last_modified\":\"2023-08-31T10:06:16.022Z\"}],\"last_modified\":\"2023-08-31T10:06:16.022Z\"}}"
    }
}

open class FakeDuckAddressSyncableSetting() : SyncableSetting {
    override val key: String = "fake_setting"

    override fun getValue(): String = "fake_value"
    override fun save(value: String?) {
        //no-op
    }

    override fun mergeRemote(value: String?) {
        //no-op
    }
}
