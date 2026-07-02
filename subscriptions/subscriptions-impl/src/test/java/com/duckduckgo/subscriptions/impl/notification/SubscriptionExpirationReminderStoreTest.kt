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

package com.duckduckgo.subscriptions.impl.notification

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SubscriptionExpirationReminderStoreTest {

    private val sharedPreferencesProvider: SharedPreferencesProvider = mock()
    private val sharedPreferences: SharedPreferences = mock()
    private val editor: Editor = mock()

    private lateinit var testee: SubscriptionExpirationReminderStore

    @Before
    fun before() {
        whenever(sharedPreferencesProvider.getSharedPreferences(SubscriptionExpirationReminderStore.FILENAME)).thenReturn(sharedPreferences)
        whenever(sharedPreferences.edit()).thenReturn(editor)
        whenever(editor.putInt(any(), any())).thenReturn(editor)
        whenever(editor.remove(any())).thenReturn(editor)
        testee = SubscriptionExpirationReminderStore(sharedPreferencesProvider)
    }

    @Test
    fun whenDaysBeforeCancelNotStoredThenGetReturnsNull() {
        whenever(sharedPreferences.getInt(SubscriptionExpirationReminderStore.KEY_DAYS_BEFORE_CANCEL, -1)).thenReturn(-1)

        assertNull(testee.daysBeforeCancel)
    }

    @Test
    fun whenDaysBeforeCancelStoredThenGetReturnsValue() {
        whenever(sharedPreferences.getInt(SubscriptionExpirationReminderStore.KEY_DAYS_BEFORE_CANCEL, -1)).thenReturn(7)

        assertEquals(7, testee.daysBeforeCancel)
    }

    @Test
    fun whenSetDaysBeforeCancelThenEditorPutsValue() {
        testee.daysBeforeCancel = 3

        verify(editor).putInt(SubscriptionExpirationReminderStore.KEY_DAYS_BEFORE_CANCEL, 3)
    }

    @Test
    fun whenSetDaysBeforeCancelToNullThenEditorRemovesKey() {
        testee.daysBeforeCancel = null

        verify(editor).remove(SubscriptionExpirationReminderStore.KEY_DAYS_BEFORE_CANCEL)
    }
}
