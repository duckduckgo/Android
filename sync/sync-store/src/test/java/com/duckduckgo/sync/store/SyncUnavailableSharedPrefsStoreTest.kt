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

package com.duckduckgo.sync.store

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncUnavailableSharedPrefsStoreTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val store = SyncUnavailableSharedPrefsStore(
        TestSharedPrefsProvider(context),
        coroutineRule.testScope,
        coroutineRule.testDispatcherProvider,
        true,
    )

    @Test
    fun whenIsSyncUnavailableIsSetThenItIsStored() = runTest {
        store.setSyncUnavailable(true)
        assertTrue(store.isSyncUnavailable())
    }

    @Test
    fun whenClearErrorIsCalledThenErrorIsClearedExceptNotifiedAt() = runTest {
        val timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        store.setSyncUnavailable(true)
        store.setSyncErrorCount(100)
        store.setSyncUnavailableSince(timestamp)
        store.setUserNotifiedAt(timestamp)

        store.clearError()

        assertFalse(store.isSyncUnavailable())
        assertEquals(0, store.getSyncErrorCount())
        assertEquals("", store.getSyncUnavailableSince())
        assertEquals(timestamp, store.getUserNotifiedAt())
    }

    @Test
    fun whenClearAllThenStoreEmpty() = runTest {
        val timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        store.setSyncUnavailable(true)
        store.setSyncErrorCount(100)
        store.setSyncUnavailableSince(timestamp)
        store.setUserNotifiedAt(timestamp)

        store.clearAll()

        assertFalse(store.isSyncUnavailable())
        assertEquals(0, store.getSyncErrorCount())
        assertEquals("", store.getSyncUnavailableSince())
        assertEquals("", store.getUserNotifiedAt())
    }
}
