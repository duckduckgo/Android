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

package com.duckduckgo.app.browser.duckchat

import android.content.SharedPreferences
import com.duckduckgo.app.browser.duckchat.RealDuckChatPreferencesStore.Companion.USER_PREFERENCES
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RealDuckChatPreferencesStoreTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var preferences: SharedPreferences
    private lateinit var testee: RealDuckChatPreferencesStore

    @Before
    fun setup() = runTest {
        preferences = InMemorySharedPreferences()

        testee = RealDuckChatPreferencesStore(
            object : SharedPreferencesProvider {
                override fun getSharedPreferences(name: String, multiprocess: Boolean, migrate: Boolean): SharedPreferences {
                    return preferences
                }

                override fun getEncryptedSharedPreferences(name: String, multiprocess: Boolean): SharedPreferences {
                    return preferences
                }
            },
        )
    }

    @Test
    fun whenFetchAndClearUserPreferencesAndPreferencesExistThenReturnAndClearPreferences() = runTest {
        val storedPreferences = "userPreferences"
        preferences.edit().putString(USER_PREFERENCES, storedPreferences).apply()

        val result = testee.fetchAndClearUserPreferences()

        assertEquals(storedPreferences, result)
        assertNull(preferences.getString(USER_PREFERENCES, null))
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

        assertEquals(newPreferences, preferences.getString(USER_PREFERENCES, null))
    }

    @Test
    fun whenUpdateUserPreferencesWithNullThenStoreNullPreferences() = runTest {
        testee.updateUserPreferences(null)

        assertNull(preferences.getString(USER_PREFERENCES, null))
    }
}
