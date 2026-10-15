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

package com.duckduckgo.pir.impl.store

import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealPirDataStoreTest {

    private val preferences = InMemorySharedPreferences()
    private val mockSharedPreferencesProvider: SharedPreferencesProvider = mock()

    private lateinit var testee: RealPirDataStore

    @Before
    fun setUp() {
        // Only the multiprocess file is stubbed: the dashboard marks the flag in :main and the run takes it in :pir.
        whenever(mockSharedPreferencesProvider.getSharedPreferences(any(), eq(true), any())).thenReturn(preferences)

        testee = RealPirDataStore(mockSharedPreferencesProvider)
    }

    @Test
    fun whenNothingIsPendingThenTakeReturnsFalse() {
        assertFalse(testee.takePendingScanCompleteNotification())
    }

    @Test
    fun whenMarkedPendingThenItIsTakenOnlyOnce() {
        testee.markScanCompleteNotificationPending()

        assertTrue(testee.takePendingScanCompleteNotification())
        assertFalse(testee.takePendingScanCompleteNotification())
    }

    @Test
    fun whenMarkedPendingTwiceThenItIsStillTakenOnlyOnce() {
        testee.markScanCompleteNotificationPending()
        testee.markScanCompleteNotificationPending()

        assertTrue(testee.takePendingScanCompleteNotification())
        assertFalse(testee.takePendingScanCompleteNotification())
    }

    @Test
    fun whenUserDataIsResetThenPendingScanCompleteNotificationIsCleared() {
        testee.markScanCompleteNotificationPending()

        testee.resetUserData()

        assertFalse(testee.takePendingScanCompleteNotification())
    }

    @Test
    fun whenMarkedPendingThenItIsStoredUnderItsKey() {
        testee.markScanCompleteNotificationPending()

        assertTrue(preferences.getBoolean(KEY_SCAN_COMPLETE_NOTIFICATION_PENDING, false))
    }

    private companion object {
        // Hardcoded, not shared with production, so renaming the persisted key fails a test instead of silently dropping a pending notification.
        const val KEY_SCAN_COMPLETE_NOTIFICATION_PENDING = "KEY_SCAN_COMPLETE_NOTIFICATION_PENDING"
    }
}
