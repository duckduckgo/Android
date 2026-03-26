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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class SyncUnavailableSharedPrefsStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val store = SyncUnavailableSharedPrefsStore(TestSharedPrefsProvider(context))

    @Test
    fun whenIsSyncUnavailableIsSetThenItIsStored() {
        store.isSyncUnavailable = true
        assertTrue(store.isSyncUnavailable)
    }

    @Test
    fun whenClearErrorIsCalledThenErrorIsClearedExceptNotifiedAt() {
        val timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        store.isSyncUnavailable = true
        store.syncErrorCount = 100
        store.syncUnavailableSince = timestamp
        store.userNotifiedAt = timestamp

        store.clearError()

        assertFalse(store.isSyncUnavailable)
        assertEquals(0, store.syncErrorCount)
        assertEquals("", store.syncUnavailableSince)
        assertEquals(timestamp, store.userNotifiedAt)
    }

    @Test
    fun whenClearAllThenStoreEmpty() {
        val timestamp = OffsetDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        store.isSyncUnavailable = true
        store.syncErrorCount = 100
        store.syncUnavailableSince = timestamp
        store.userNotifiedAt = timestamp

        store.clearAll()

        assertFalse(store.isSyncUnavailable)
        assertEquals(0, store.syncErrorCount)
        assertEquals("", store.syncUnavailableSince)
        assertEquals("", store.userNotifiedAt)
    }
}
