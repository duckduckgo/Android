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
import com.duckduckgo.remote.messaging.api.Content.MessageType
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aCardsListMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aSmallMessage
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
    fun whenInitializedThenLastShownRemoteMessageTypeIsNull() = runTest {
        assertNull(testee.getLastShownRemoteMessageType())
    }

    @Test
    fun whenLastShownRemoteMessageRecordedThenIdIsRetrieved() = runTest {
        val message = aCardsListMessage(id = "test-message-id")

        testee.recordLastShownRemoteMessage(message)

        assertEquals("test-message-id", testee.getLastShownRemoteMessageId())
    }

    @Test
    fun whenLastShownRemoteMessageRecordedThenTypeIsRetrieved() = runTest {
        val message = aCardsListMessage(id = "test-message-id")

        testee.recordLastShownRemoteMessage(message)

        assertEquals(MessageType.CARDS_LIST, testee.getLastShownRemoteMessageType())
    }

    @Test
    fun whenLastShownRemoteMessageRecordedMultipleTimesThenLatestValuesAreStored() = runTest {
        val firstMessage = aSmallMessage(id = "first-message-id")
        val secondMessage = aCardsListMessage(id = "second-message-id")

        testee.recordLastShownRemoteMessage(firstMessage)
        assertEquals("first-message-id", testee.getLastShownRemoteMessageId())
        assertEquals(MessageType.SMALL, testee.getLastShownRemoteMessageType())

        testee.recordLastShownRemoteMessage(secondMessage)
        assertEquals("second-message-id", testee.getLastShownRemoteMessageId())
        assertEquals(MessageType.CARDS_LIST, testee.getLastShownRemoteMessageType())
    }
}
