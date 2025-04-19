/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.sync.SyncCredentialsListener.Companion.SYNC_CREDENTIALS_DELETE_DELAY
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SyncCredentialsListenerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val autofillDB = inMemoryAutofillDatabase()
    private val syncMetatadaDao = autofillDB.credentialsSyncDao()

    val testee = SyncCredentialsListener(
        credentialsSyncMetadata = CredentialsSyncMetadata(syncMetatadaDao),
        dispatcherProvider = coroutineRule.testDispatcherProvider,
        appCoroutineScope = coroutineRule.testScope,
    )

    @After
    fun after() {
        autofillDB.close()
    }

    @Test
    fun whenOnCredentialAddedThenNotifySyncMetadata() {
        testee.onCredentialAdded(1)

        syncMetatadaDao.getSyncMetadata(1)?.let {
            assertEquals(1, it.localId)
            assertNull(it.deleted_at)
            assertNotNull(it.modified_at)
        } ?: fail("Sync metadata not found")
    }

    @Test
    fun whenAddingCredentialRecentlyRemovedThenCancelDeleteOperationAndDoNotUpdateMetadata() = runTest {
        testee.onCredentialAdded(1)
        val credential = syncMetatadaDao.getSyncMetadata(1)
        testee.onCredentialRemoved(1)
        testee.onCredentialAdded(1)
        this.advanceTimeBy(SYNC_CREDENTIALS_DELETE_DELAY + 1)

        syncMetatadaDao.getSyncMetadata(1)?.let {
            assertEquals(credential, it)
        } ?: fail("Sync metadata not found")
    }

    @Test
    fun whenCredentialNotReinsertedThenNotifySyncMetadata() = runTest {
        testee.onCredentialAdded(1)
        testee.onCredentialRemoved(1)
        this.advanceTimeBy(SYNC_CREDENTIALS_DELETE_DELAY + 1)

        syncMetatadaDao.getSyncMetadata(1)?.let {
            assertNotNull(it.deleted_at)
        } ?: fail("Sync metadata not found")
    }

    @Test
    fun whenMultipleCredentialsAddedThenNotifySyncMetadata() {
        testee.onCredentialsAdded(listOf(1, 2, 3, 4, 5))

        assertEquals(5, syncMetatadaDao.getAll().size)
        syncMetatadaDao.getSyncMetadata(1)?.let {
            assertEquals(1, it.localId)
            assertNull(it.deleted_at)
            assertNotNull(it.modified_at)
        } ?: fail("Sync metadata not found")
    }

    @Test
    fun whenReinsertingCredentialsRecentlyRemovedThenCancelDeleteOperationAndDoNotUpdateMetadata() = runTest {
        testee.onCredentialsAdded(listOf(1, 2, 3, 4, 5))
        val credentials = syncMetatadaDao.getAll()
        assertEquals(5, credentials.size)

        testee.onCredentialRemoved(listOf(1, 2, 3, 4, 5))
        testee.onCredentialsAdded(listOf(1, 2, 3, 4, 5))
        this.advanceTimeBy(SYNC_CREDENTIALS_DELETE_DELAY + 1)

        assertEquals(5, syncMetatadaDao.getAll().size)
        syncMetatadaDao.getAll().forEachIndexed { index, syncMetadataEntity ->
            assertEquals(credentials[index], syncMetadataEntity)
        }
    }

    @Test
    fun whenCredentialsNotReinsertedThenNotifySyncMetadata() = runTest {
        testee.onCredentialsAdded(listOf(1, 2, 3, 4, 5))

        testee.onCredentialRemoved(listOf(1, 2, 3, 4, 5))
        this.advanceTimeBy(SYNC_CREDENTIALS_DELETE_DELAY + 1)

        syncMetatadaDao.getAll().forEach {
            assertNotNull(it.deleted_at)
        }
    }
}
