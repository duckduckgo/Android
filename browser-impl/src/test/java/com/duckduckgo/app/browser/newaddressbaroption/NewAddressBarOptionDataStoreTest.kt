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

package com.duckduckgo.app.browser.newaddressbaroption

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewAddressBarOptionDataStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val testDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = coroutineRule.testScope,
            produceFile = { context.preferencesDataStoreFile("new_address_bar_option_store") },
        )

    private val testee: NewAddressBarOptionDataStore =
        SharedPreferencesNewAddressBarOptionDataStore(
            testDataStore,
            coroutineRule.testDispatcherProvider,
        )

    companion object {
        val WAS_SHOWN_KEY = booleanPreferencesKey("NEW_ADDRESS_BAR_OPTION_WAS_SHOWN")
        val WAS_VALIDATED_KEY = booleanPreferencesKey("NEW_ADDRESS_BAR_OPTION_WAS_VALIDATED")
    }

    @Test
    fun `when wasShown called initially then returns false`() = runTest {
        assertFalse(testee.wasShown())
    }

    @Test
    fun `when setAsShown called then wasShown returns true`() = runTest {
        testee.setAsShown()
        assertTrue(testee.wasShown())
        assertTrue(testDataStore.data.firstOrNull()?.get(WAS_SHOWN_KEY) == true)
    }

    @Test
    fun `when wasValidated called initially then returns false`() = runTest {
        assertFalse(testee.wasValidated())
    }

    @Test
    fun `when setAsValidated called then wasValidated returns true`() = runTest {
        testee.setAsValidated()
        assertTrue(testee.wasValidated())
        assertTrue(testDataStore.data.firstOrNull()?.get(WAS_VALIDATED_KEY) == true)
    }
}
