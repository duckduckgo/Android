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

package com.duckduckgo.settings.impl.serpsettings.store

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

@Suppress("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class SerpSettingsPrefsDataStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val testDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("serp_settings_test") },
        )

    private val testee: SerpSettingsDataStore =
        SerpSettingsPrefsDataStore(testDataStore)

    companion object {
        val SERP_SETTINGS = stringPreferencesKey("SERP_SETTINGS")
    }

    @Test
    fun whenGetSerpSettingsAndNoSettingsExistThenReturnNull() = runTest {
        val result = testee.getSerpSettings()

        assertNull(result)
    }

    @Test
    fun whenSetSerpSettingsThenStoreProvidedSettings() = runTest {
        val settings = """{"isDuckAiEnabled":true,"duckAiTitle":"Duck.AI"}"""

        testee.setSerpSettings(settings)

        assertEquals(settings, testDataStore.data.first()[SERP_SETTINGS])
    }

    @Test
    fun whenGetSerpSettingsAndSettingsExistThenReturnSettings() = runTest {
        val settings = """{"ko":"1","kg":"2"}"""
        testDataStore.updateData { current ->
            current.toMutablePreferences().apply {
                this[SERP_SETTINGS] = settings
            }
        }

        val result = testee.getSerpSettings()

        assertEquals(settings, result)
    }

    @Test
    fun whenSetSerpSettingsMultipleTimesThenStoreLatestSettings() = runTest {
        val firstSettings = """{"ko":"1"}"""
        val secondSettings = """{"ko":"2","kg":"3"}"""

        testee.setSerpSettings(firstSettings)
        assertEquals(firstSettings, testee.getSerpSettings())

        testee.setSerpSettings(secondSettings)
        assertEquals(secondSettings, testee.getSerpSettings())
    }

    @Test
    fun whenSetEmptyJsonThenStoreEmptyJson() = runTest {
        val emptyJson = "{}"

        testee.setSerpSettings(emptyJson)

        assertEquals(emptyJson, testee.getSerpSettings())
    }

    @Test
    fun whenDataStoreIsEmptyThenGetReturnsNull() = runTest {
        assertFalse(testDataStore.data.first().contains(SERP_SETTINGS))

        val result = testee.getSerpSettings()

        assertNull(result)
    }
}
