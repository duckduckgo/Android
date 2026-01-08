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

package com.duckduckgo.remote.messaging.impl.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private const val DATA_STORE_NAME: String = "modal_surface_store_feature_test_data_store"

@RunWith(AndroidJUnit4::class)
class ModalSurfaceStoreImplTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testDataStoreFile: File
    private lateinit var testDataStore: DataStore<Preferences>

    private lateinit var testee: ModalSurfaceStoreImpl

    @Before
    fun setUp() {
        testDataStoreFile = File.createTempFile(DATA_STORE_NAME, ".preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            scope = coroutinesTestRule.testScope,
            produceFile = { testDataStoreFile },
        )
        testee = ModalSurfaceStoreImpl(
            store = testDataStore,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
        )
    }

    @After
    fun tearDown() {
        testDataStoreFile.delete()
    }

    @Test
    fun whenInitializedThenBackgroundedTimestampIsNull() = runTest {
        assertNull(testee.getBackgroundedTimestamp())
    }

    @Test
    fun whenBackgroundedTimestampRecordedThenValueIsRetrieved() = runTest {
        testee.recordBackgroundedTimestamp()

        val timestamp = testee.getBackgroundedTimestamp()
        assertNotNull(timestamp)
    }

    @Test
    fun whenBackgroundedTimestampClearedThenValueIsNull() = runTest {
        testee.recordBackgroundedTimestamp()
        assertNotNull(testee.getBackgroundedTimestamp())

        testee.clearBackgroundTimestamp()

        assertNull(testee.getBackgroundedTimestamp())
    }

    @Test
    fun whenBackgroundedTimestampRecordedMultipleTimesThenLatestValueIsStored() = runTest {
        testee.recordBackgroundedTimestamp()
        val firstTimestamp = testee.getBackgroundedTimestamp()
        assertNotNull(firstTimestamp)

        // Small delay to ensure different timestamp
        Thread.sleep(10)

        testee.recordBackgroundedTimestamp()
        val secondTimestamp = testee.getBackgroundedTimestamp()
        assertNotNull(secondTimestamp)

        // Second timestamp should be greater than or equal to first
        assertTrue(secondTimestamp!! >= firstTimestamp!!)
    }

    @Test
    fun whenClearBackgroundTimestampCalledWithoutRecordingThenNoError() = runTest {
        assertNull(testee.getBackgroundedTimestamp())

        testee.clearBackgroundTimestamp()

        assertNull(testee.getBackgroundedTimestamp())
    }

    @Test
    fun whenInitializedThenLastShownRemoteMessageIdIsNull() = runTest {
        assertNull(testee.getLastShownRemoteMessageId())
    }

    @Test
    fun whenLastShownRemoteMessageIdRecordedThenValueIsRetrieved() = runTest {
        val messageId = "test-message-id"

        testee.recordLastShownRemoteMessageId(messageId)

        assertEquals(messageId, testee.getLastShownRemoteMessageId())
    }

    @Test
    fun whenLastShownRemoteMessageIdClearedThenValueIsNull() = runTest {
        val messageId = "test-message-id"
        testee.recordLastShownRemoteMessageId(messageId)
        assertEquals(messageId, testee.getLastShownRemoteMessageId())

        testee.clearLastShownRemoteMessageId()

        assertNull(testee.getLastShownRemoteMessageId())
    }

    @Test
    fun whenLastShownRemoteMessageIdRecordedMultipleTimesThenLatestValueIsStored() = runTest {
        val firstMessageId = "first-message-id"
        val secondMessageId = "second-message-id"

        testee.recordLastShownRemoteMessageId(firstMessageId)
        assertEquals(firstMessageId, testee.getLastShownRemoteMessageId())

        testee.recordLastShownRemoteMessageId(secondMessageId)
        assertEquals(secondMessageId, testee.getLastShownRemoteMessageId())
    }

    @Test
    fun whenClearLastShownRemoteMessageIdCalledWithoutRecordingThenNoError() = runTest {
        assertNull(testee.getLastShownRemoteMessageId())

        testee.clearLastShownRemoteMessageId()

        assertNull(testee.getLastShownRemoteMessageId())
    }
}
