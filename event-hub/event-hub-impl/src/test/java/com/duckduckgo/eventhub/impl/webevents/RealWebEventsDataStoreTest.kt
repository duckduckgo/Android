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

package com.duckduckgo.eventhub.impl.webevents

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class RealWebEventsDataStoreTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule(UnconfinedTestDispatcher())

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun createDataStore(): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            scope = coroutineTestRule.testScope,
            produceFile = { tmpFolder.newFile("test_prefs.preferences_pb") },
        )
    }

    private fun createSubject(store: DataStore<Preferences> = createDataStore()): RealWebEventsDataStore {
        return RealWebEventsDataStore(
            store = store,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun `initial value is empty JSON object`() = runTest {
        val subject = createSubject()
        advanceUntilIdle()

        assertEquals("{}", subject.getWebEventsConfigJson())
    }

    @Test
    fun `set then get returns stored value`() = runTest {
        val subject = createSubject()
        advanceUntilIdle()

        val json = """{"telemetry":{"pixel1":{}}}"""
        subject.setWebEventsConfigJson(json)
        advanceUntilIdle()

        assertEquals(json, subject.getWebEventsConfigJson())
    }

    @Test
    fun `overwriting value returns latest`() = runTest {
        val subject = createSubject()
        advanceUntilIdle()

        subject.setWebEventsConfigJson("""{"first":true}""")
        advanceUntilIdle()

        val updated = """{"second":true}"""
        subject.setWebEventsConfigJson(updated)
        advanceUntilIdle()

        assertEquals(updated, subject.getWebEventsConfigJson())
    }

    @Test
    fun `volatile cache is available synchronously after set`() = runTest {
        val subject = createSubject()
        advanceUntilIdle()

        val json = """{"cached":true}"""
        subject.setWebEventsConfigJson(json)

        assertEquals(json, subject.getWebEventsConfigJson())
    }

    @Test
    fun `persisted value survives new instance on same DataStore`() = runTest {
        val store = createDataStore()
        val first = createSubject(store)
        advanceUntilIdle()

        val json = """{"persisted":true}"""
        first.setWebEventsConfigJson(json)
        advanceUntilIdle()

        val second = createSubject(store)
        advanceUntilIdle()

        assertEquals(json, second.getWebEventsConfigJson())
    }
}
