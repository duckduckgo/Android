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

package com.duckduckgo.duckchat.impl.inputscreen.ui.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealInputScreenSessionStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("input_screen_session_store") },
        )

    private val testee: InputScreenSessionStore =
        RealInputScreenSessionStore(dataStore)

    @Test
    fun `when on init then has not used search or chat`() = runTest {
        assertFalse(testee.hasUsedSearchMode())
        assertFalse(testee.hasUsedChatMode())
    }

    @Test
    fun `when setHasUsedSearchMode to true then hasUsedSearchMode returns true`() = runTest {
        testee.setHasUsedSearchMode(true)
        assertTrue(testee.hasUsedSearchMode())
    }

    @Test
    fun `when setHasUsedChatMode to true then hasUsedChatMode returns true`() = runTest {
        testee.setHasUsedChatMode(true)
        assertTrue(testee.hasUsedChatMode())
    }

    @Test
    fun `when resetSession then both flags are false`() = runTest {
        testee.setHasUsedSearchMode(true)
        testee.setHasUsedChatMode(true)
        assertTrue(testee.hasUsedSearchMode())
        assertTrue(testee.hasUsedChatMode())

        testee.resetSession()

        assertFalse(testee.hasUsedSearchMode())
        assertFalse(testee.hasUsedChatMode())
    }

    @Test
    fun `when keys absent then return false`() = runTest {
        dataStore.edit { it.clear() }

        assertFalse(testee.hasUsedSearchMode())
        assertFalse(testee.hasUsedChatMode())
    }
}
