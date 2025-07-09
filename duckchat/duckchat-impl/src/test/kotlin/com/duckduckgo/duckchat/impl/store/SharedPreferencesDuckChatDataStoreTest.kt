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
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
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
        SharedPreferencesDuckChatDataStore(
            testDataStore,
            coroutineRule.testDispatcherProvider,
            true,
            coroutineRule.testScope,
        )

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

    @Test
    fun whenIsDuckChatUserEnabledDefaultThenReturnTrue() = runTest {
        assertTrue(testee.isDuckChatUserEnabled())
    }

    @Test
    fun whenGetShowInBrowserMenuDefaultThenReturnTrue() = runTest {
        assertTrue(testee.getShowInBrowserMenu())
    }

    @Test
    fun whenGetShowInAddressBarDefaultThenFollowMenuDefault() = runTest {
        assertTrue(testee.getShowInBrowserMenu())
        assertTrue(testee.getShowInAddressBar())
    }

    @Test
    fun `when isInputScreenUserSettingEnabled then return default value`() = runTest {
        assertFalse(testee.isInputScreenUserSettingEnabled())
    }

    @Test
    fun whenMenuFlagChangesLaterThenAddressBarRemainsUnchanged() = runTest {
        assertTrue(testee.getShowInBrowserMenu())
        assertTrue(testee.getShowInAddressBar())

        testee.setShowInBrowserMenu(false)

        assertFalse(testee.getShowInBrowserMenu())
        assertTrue(testee.getShowInAddressBar())
    }

    @Test
    fun whenSetDuckChatUserEnabledThenIsDuckChatUserEnabledThenReturnValue() = runTest {
        testee.setDuckChatUserEnabled(false)
        assertFalse(testee.isDuckChatUserEnabled())
    }

    @Test
    fun whenSetShowInBrowserMenuThenGetShowInBrowserMenuThenReturnValue() = runTest {
        testee.setShowInBrowserMenu(false)
        assertFalse(testee.getShowInBrowserMenu())
    }

    @Test
    fun whenSetShowInAddressBarThenGetShowInAddressBarThenReturnValue() = runTest {
        testee.setShowInAddressBar(false)
        assertFalse(testee.getShowInAddressBar())
    }

    @Test
    fun `when setInputScreenUserSetting then return value`() = runTest {
        testee.setInputScreenUserSetting(false)
        assertFalse(testee.isInputScreenUserSettingEnabled())
    }

    @Test
    fun whenObserveDuckChatUserEnabledThenReceiveUpdates() = runTest {
        val results = mutableListOf<Boolean>()
        val job = launch {
            testee.observeDuckChatUserEnabled()
                .take(2)
                .toList(results)
        }
        testee.setDuckChatUserEnabled(false)
        job.join()

        assertEquals(listOf(true, false), results)
    }

    @Test
    fun whenObserveShowInBrowserMenuThenReceiveUpdates() = runTest {
        val results = mutableListOf<Boolean>()
        val job = launch {
            testee.observeShowInBrowserMenu()
                .take(2)
                .toList(results)
        }
        testee.setShowInBrowserMenu(false)
        job.join()

        assertEquals(listOf(true, false), results)
    }

    @Test
    fun whenObserveShowInAddressBarThenReceiveUpdates() = runTest {
        val results = mutableListOf<Boolean>()
        val job = launch {
            testee.observeShowInAddressBar()
                .take(2)
                .toList(results)
        }
        testee.setShowInAddressBar(false)
        job.join()

        assertEquals(listOf(true, false), results)
    }

    @Test
    fun `when observeInputScreenUserSettingEnabled then receive updates`() = runTest {
        val results = mutableListOf<Boolean>()
        val job = launch {
            testee.observeInputScreenUserSettingEnabled()
                .take(2)
                .toList(results)
        }
        testee.setInputScreenUserSetting(true)
        job.join()

        assertEquals(listOf(false, true), results)
    }

    @Test
    fun whenRegisterOpenedThenWasOpenedBeforeThenReturnTrue() = runTest {
        assertFalse(testee.wasOpenedBefore())
        testee.registerOpened()
        assertTrue(testee.wasOpenedBefore())
    }
}
