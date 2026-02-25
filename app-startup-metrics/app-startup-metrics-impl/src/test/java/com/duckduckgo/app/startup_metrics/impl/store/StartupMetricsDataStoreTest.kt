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

package com.duckduckgo.app.startup_metrics.impl.store

import android.content.SharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StartupMetricsDataStoreTest {

    private lateinit var sharedPreferencesProvider: SharedPreferencesProvider
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var dataStore: RealStartupMetricsDataStore

    @Before
    fun setup() {
        sharedPreferencesProvider = mock()
        sharedPreferences = mock()
        editor = mock()

        whenever(sharedPreferencesProvider.getSharedPreferences(any(), any(), any())).thenReturn(sharedPreferences)
        whenever(sharedPreferences.edit()).thenReturn(editor)
        whenever(editor.putLong(any(), any())).thenReturn(editor)

        dataStore = RealStartupMetricsDataStore(sharedPreferencesProvider)
    }

    @Test
    fun `when getLastCollectedLaunchTime called then returns value from SharedPreferences`() {
        val expectedTimestamp = 1234567890L
        whenever(sharedPreferences.getLong(eq("last_collected_launch_time_ms"), eq(0L)))
            .thenReturn(expectedTimestamp)

        val result = dataStore.getLastCollectedLaunchTime()

        assertEquals(expectedTimestamp, result)
        verify(sharedPreferences).getLong("last_collected_launch_time_ms", 0L)
    }

    @Test
    fun `when getLastCollectedLaunchTime called and no value stored then returns 0`() {
        whenever(sharedPreferences.getLong(eq("last_collected_launch_time_ms"), eq(0L)))
            .thenReturn(0L)

        val result = dataStore.getLastCollectedLaunchTime()

        assertEquals(0L, result)
    }

    @Test
    fun `when setLastCollectedLaunchTime called then stores value in SharedPreferences`() {
        val timestamp = 9876543210L
        whenever(editor.putLong(any(), any())).thenReturn(editor)

        dataStore.setLastCollectedLaunchTime(timestamp)

        verify(editor).putLong("last_collected_launch_time_ms", timestamp)
    }
}
