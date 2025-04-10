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

package com.duckduckgo.duckchat.impl.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesDuckChatDataStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val testDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("duck_chat_store") },
        )

    private val testee: DuckChatDataStore =
        SharedPreferencesDuckChatDataStore(testDataStore, coroutineRule.testScope)

    companion object {
        val DUCK_CHAT_USER_PREFERENCES = stringPreferencesKey("DUCK_CHAT_USER_PREFERENCES")
    }

    @Test
    fun whenFetchAndClearUserPreferencesAndPreferencesExistThenReturnAndClearPreferences() = runTest {
        val storedPreferences = "userPreferences"
        testDataStore.updateData { current ->
            current.toMutablePreferences().apply {
                this[DUCK_CHAT_USER_PREFERENCES] = storedPreferences
            }
        }

        val result = testee.fetchAndClearUserPreferences()

        assertEquals(storedPreferences, result)
        assertFalse(testDataStore.data.first().contains(DUCK_CHAT_USER_PREFERENCES))
        assertNull(testee.fetchAndClearUserPreferences())
    }

    @Test
    fun whenFetchAndClearUserPreferencesAndNoPreferencesExistThenReturnNull() = runTest {
        val result = testee.fetchAndClearUserPreferences()

        assertNull(result)
    }

    @Test
    fun whenUpdateUserPreferencesThenStoreProvidedPreferences() = runTest {
        val newPreferences = "newUserPreferences"

        testee.updateUserPreferences(newPreferences)

        assertEquals(newPreferences, testDataStore.data.first()[DUCK_CHAT_USER_PREFERENCES])
    }

    @Test
    fun whenUpdateUserPreferencesWithNullThenStoreNullPreferences() = runTest {
        testee.updateUserPreferences(null)

        assertFalse(testDataStore.data.first().contains(DUCK_CHAT_USER_PREFERENCES))
    }
}
