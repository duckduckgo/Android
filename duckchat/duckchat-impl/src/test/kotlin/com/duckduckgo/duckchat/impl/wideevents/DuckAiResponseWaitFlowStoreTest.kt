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

package com.duckduckgo.duckchat.impl.wideevents

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuckAiResponseWaitFlowStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val temporaryFolder = TemporaryFolder.builder().assureDeletion().build().also { it.create() }
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = coroutineRule.testScope,
        produceFile = { temporaryFolder.newFile("temp.preferences_pb") },
    )

    private val testee = DuckAiResponseWaitFlowStoreImpl(dataStore)

    @Test
    fun `save then load round-trips every field`() = runTest {
        val flows = listOf(
            PersistedResponseWaitFlow(
                flowId = 1L,
                tabId = "t1",
                chatId = "A",
                left = true,
                returned = false,
                done = true,
                result = "ready",
                readyWhileAwayRecorded = true,
                readyToReturnRunning = true,
            ),
            PersistedResponseWaitFlow(
                flowId = 2L,
                tabId = "t2",
                chatId = null,
                left = false,
                returned = true,
                done = false,
                result = "unknown",
                readyWhileAwayRecorded = false,
                readyToReturnRunning = false,
            ),
        )

        testee.save(flows)

        assertEquals(flows, testee.load())
    }

    @Test
    fun `load with no data returns empty`() = runTest {
        assertEquals(emptyList<PersistedResponseWaitFlow>(), testee.load())
    }

    @Test
    fun `load with garbage returns empty`() = runTest {
        dataStore.edit { it[stringPreferencesKey("duckai_response_wait_open_flows")] = "not json" }

        assertEquals(emptyList<PersistedResponseWaitFlow>(), testee.load())
    }
}
