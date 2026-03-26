/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.modalcoordinator.impl.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private const val DATA_STORE_NAME: String = "modal_evaluator_completion_test_data_store"

@RunWith(AndroidJUnit4::class)
class ModalEvaluatorCompletionStoreImplTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testDataStoreFile: File
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var testee: ModalEvaluatorCompletionStoreImpl

    @Before
    fun setUp() {
        testDataStoreFile = File.createTempFile(DATA_STORE_NAME, ".preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            scope = coroutinesTestRule.testScope,
            produceFile = { testDataStoreFile },
        )
        testee = ModalEvaluatorCompletionStoreImpl(
            store = testDataStore,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
        )
    }

    @After
    fun tearDown() {
        testDataStoreFile.delete()
    }

    @Test
    fun whenInitializedThenIsNotBlockedBy24HourWindow() = runTest {
        assertFalse(testee.isBlockedBy24HourWindow())
    }

    @Test
    fun whenCompletionRecordedThenIsBlockedBy24HourWindow() = runTest {
        testee.recordCompletion()

        assertTrue(testee.isBlockedBy24HourWindow())
    }

    @Test
    fun whenMultipleCompletionsRecordedThenOnlyLatestMatters() = runTest {
        testee.recordCompletion()
        assertTrue(testee.isBlockedBy24HourWindow())

        // Record another completion
        testee.recordCompletion()

        // Should still be blocked
        assertTrue(testee.isBlockedBy24HourWindow())
    }
}
