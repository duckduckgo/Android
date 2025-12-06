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

package com.duckduckgo.app.fire.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class FireDataStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var fireDataStore: FireDataStore

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("fire_clearing_preferences_test") },
        )
        settingsDataStore = mock()
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_NONE)
        fireDataStore = SharedPreferencesFireDataStore(dataStore, settingsDataStore)
    }

    @Test
    fun whenNoManualOptionsSet_thenReturnsDefaultOptions() = runTest {
        val options = fireDataStore.getManualClearOptions()
        // Default is TABS + DATA
        assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA), options)
    }

    @Test
    fun whenManualOptionsCleared_thenReturnsEmptySet() = runTest {
        // Set options first
        fireDataStore.setManualClearOptions(setOf(FireClearOption.TABS))

        // Clear by setting empty set
        fireDataStore.setManualClearOptions(emptySet())

        // Should return empty set, not default
        val options = fireDataStore.getManualClearOptions()
        assertTrue(options.isEmpty())
    }

    @Test
    fun whenManualOptionsSet_thenReturnsCorrectOptions() = runTest {
        val expectedOptions = setOf(FireClearOption.TABS, FireClearOption.DATA)
        fireDataStore.setManualClearOptions(expectedOptions)

        val actualOptions = fireDataStore.getManualClearOptions()
        assertEquals(expectedOptions, actualOptions)
    }

    @Test
    fun whenAllManualOptionsSet_thenReturnsAllOptions() = runTest {
        val allOptions = setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS)
        fireDataStore.setManualClearOptions(allOptions)

        val actualOptions = fireDataStore.getManualClearOptions()
        assertEquals(allOptions, actualOptions)
    }

    @Test
    fun whenManualOptionAdded_thenOptionIsInSelection() = runTest {
        fireDataStore.setManualClearOptions(setOf(FireClearOption.TABS))
        fireDataStore.addManualClearOption(FireClearOption.DATA)

        val options = fireDataStore.getManualClearOptions()
        assertTrue(options.contains(FireClearOption.TABS))
        assertTrue(options.contains(FireClearOption.DATA))
        assertFalse(options.contains(FireClearOption.DUCKAI_CHATS))
    }

    @Test
    fun whenManualOptionRemoved_thenOptionIsNotInSelection() = runTest {
        fireDataStore.setManualClearOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))
        fireDataStore.removeManualClearOption(FireClearOption.TABS)

        val options = fireDataStore.getManualClearOptions()
        assertFalse(options.contains(FireClearOption.TABS))
        assertTrue(options.contains(FireClearOption.DATA))
    }

    @Test
    fun whenCheckingIfManualOptionSelected_thenReturnsCorrectResult() = runTest {
        fireDataStore.setManualClearOptions(setOf(FireClearOption.TABS))

        assertTrue(fireDataStore.isManualClearOptionSelected(FireClearOption.TABS))
        assertFalse(fireDataStore.isManualClearOptionSelected(FireClearOption.DATA))
        assertFalse(fireDataStore.isManualClearOptionSelected(FireClearOption.DUCKAI_CHATS))
    }

    @Test
    fun whenManualOptionsChanged_thenFlowEmitsNewValues() = runTest {
        fireDataStore.getManualClearOptionsFlow().test {
            // Initial default state (TABS + DATA)
            assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA), awaitItem())

            // Set options to just TABS
            fireDataStore.setManualClearOptions(setOf(FireClearOption.TABS))
            assertEquals(setOf(FireClearOption.TABS), awaitItem())

            // Add DATA option
            fireDataStore.addManualClearOption(FireClearOption.DATA)
            assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA), awaitItem())

            // Remove TABS option
            fireDataStore.removeManualClearOption(FireClearOption.TABS)
            assertEquals(setOf(FireClearOption.DATA), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNoAutomaticOptionsSet_thenReturnsEmptySet() = runTest {
        val options = fireDataStore.getAutomaticClearOptions()
        assertTrue(options.isEmpty())
    }

    @Test
    fun whenAutomaticOptionsSet_thenReturnsCorrectOptions() = runTest {
        val expectedOptions = setOf(FireClearOption.TABS, FireClearOption.DATA)
        fireDataStore.setAutomaticClearOptions(expectedOptions)

        val actualOptions = fireDataStore.getAutomaticClearOptions()
        assertEquals(expectedOptions, actualOptions)
    }

    @Test
    fun whenAllAutomaticOptionsSet_thenReturnsAllOptions() = runTest {
        val allOptions = setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS)
        fireDataStore.setAutomaticClearOptions(allOptions)

        val actualOptions = fireDataStore.getAutomaticClearOptions()
        assertEquals(allOptions, actualOptions)
    }

    @Test
    fun whenAutomaticOptionAdded_thenOptionIsInSelection() = runTest {
        fireDataStore.setAutomaticClearOptions(setOf(FireClearOption.TABS))
        fireDataStore.addAutomaticClearOption(FireClearOption.DATA)

        val options = fireDataStore.getAutomaticClearOptions()
        assertTrue(options.contains(FireClearOption.TABS))
        assertTrue(options.contains(FireClearOption.DATA))
        assertFalse(options.contains(FireClearOption.DUCKAI_CHATS))
    }

    @Test
    fun whenAutomaticOptionRemoved_thenOptionIsNotInSelection() = runTest {
        fireDataStore.setAutomaticClearOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))
        fireDataStore.removeAutomaticClearOption(FireClearOption.TABS)

        val options = fireDataStore.getAutomaticClearOptions()
        assertFalse(options.contains(FireClearOption.TABS))
        assertTrue(options.contains(FireClearOption.DATA))
    }

    @Test
    fun whenCheckingIfAutomaticOptionSelected_thenReturnsCorrectResult() = runTest {
        fireDataStore.setAutomaticClearOptions(setOf(FireClearOption.TABS))

        assertTrue(fireDataStore.isAutomaticClearOptionSelected(FireClearOption.TABS))
        assertFalse(fireDataStore.isAutomaticClearOptionSelected(FireClearOption.DATA))
        assertFalse(fireDataStore.isAutomaticClearOptionSelected(FireClearOption.DUCKAI_CHATS))
    }

    @Test
    fun whenAutomaticOptionsChanged_thenFlowEmitsNewValues() = runTest {
        fireDataStore.getAutomaticClearOptionsFlow().test {
            // Initial empty state
            assertEquals(emptySet<FireClearOption>(), awaitItem())

            // Set options
            fireDataStore.setAutomaticClearOptions(setOf(FireClearOption.TABS))
            assertEquals(setOf(FireClearOption.TABS), awaitItem())

            // Add option
            fireDataStore.addAutomaticClearOption(FireClearOption.DATA)
            assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA), awaitItem())

            // Remove option
            fireDataStore.removeAutomaticClearOption(FireClearOption.TABS)
            assertEquals(setOf(FireClearOption.DATA), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // Test Independence of Both Sets
    @Test
    fun whenBothSetsConfigured_thenTheyAreIndependent() = runTest {
        // Set different options for each
        fireDataStore.setManualClearOptions(setOf(FireClearOption.TABS))
        fireDataStore.setAutomaticClearOptions(setOf(FireClearOption.DATA, FireClearOption.DUCKAI_CHATS))

        // Verify they are stored independently
        val manual = fireDataStore.getManualClearOptions()
        val automatic = fireDataStore.getAutomaticClearOptions()

        assertEquals(setOf(FireClearOption.TABS), manual)
        assertEquals(setOf(FireClearOption.DATA, FireClearOption.DUCKAI_CHATS), automatic)
    }

    @Test
    fun whenManualCleared_thenAutomaticUnaffected() = runTest {
        // Set both
        fireDataStore.setManualClearOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))
        fireDataStore.setAutomaticClearOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))

        // Clear manual
        fireDataStore.setManualClearOptions(emptySet())

        // Verify automatic is unchanged
        val manual = fireDataStore.getManualClearOptions()
        val automatic = fireDataStore.getAutomaticClearOptions()

        assertTrue(manual.isEmpty())
        assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA), automatic)
    }

    @Test
    fun whenAutomaticCleared_thenManualUnaffected() = runTest {
        // Set both
        fireDataStore.setManualClearOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))
        fireDataStore.setAutomaticClearOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))

        // Clear automatic
        fireDataStore.setAutomaticClearOptions(emptySet())

        // Verify manual is unchanged
        val manual = fireDataStore.getManualClearOptions()
        val automatic = fireDataStore.getAutomaticClearOptions()

        assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA), manual)
        assertTrue(automatic.isEmpty())
    }

    // Migration Tests
    @Test
    fun whenNoAutomaticOptionsSet_andLegacyIsNone_thenReturnsEmptySet() = runTest {
        // Legacy setting is CLEAR_NONE (already mocked in setup)
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_NONE)

        val options = fireDataStore.getAutomaticClearOptions()

        assertTrue(options.isEmpty())
    }

    @Test
    fun whenNoAutomaticOptionsSet_andLegacyIsTabsOnly_thenReturnsTabsOnly() = runTest {
        // Set legacy setting to CLEAR_TABS_ONLY
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_TABS_ONLY)

        val options = fireDataStore.getAutomaticClearOptions()

        assertEquals(setOf(FireClearOption.TABS), options)
    }

    @Test
    fun whenNoAutomaticOptionsSet_andLegacyIsTabsAndData_thenReturnsTabsAndData() = runTest {
        // Set legacy setting to CLEAR_TABS_AND_DATA
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_TABS_AND_DATA)

        val options = fireDataStore.getAutomaticClearOptions()

        assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA), options)
    }

    @Test
    fun whenAutomaticOptionsAlreadySet_thenLegacyOptionsIgnored() = runTest {
        // Set legacy setting to CLEAR_TABS_AND_DATA
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_TABS_AND_DATA)

        // Explicitly set automatic options to something different
        fireDataStore.setAutomaticClearOptions(setOf(FireClearOption.DUCKAI_CHATS))

        // Should return stored value, not legacy value
        val options = fireDataStore.getAutomaticClearOptions()

        assertEquals(setOf(FireClearOption.DUCKAI_CHATS), options)
    }

    @Test
    fun whenNoAutomaticOptionsSet_andLegacyIsTabsOnly_thenFlowEmitsTabsOnly() = runTest {
        // Set legacy setting to CLEAR_TABS_ONLY
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_TABS_ONLY)

        fireDataStore.getAutomaticClearOptionsFlow().test {
            assertEquals(setOf(FireClearOption.TABS), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNoAutomaticOptionsSet_andLegacyIsTabsAndData_thenFlowEmitsTabsAndData() = runTest {
        // Set legacy setting to CLEAR_TABS_AND_DATA
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_TABS_AND_DATA)

        fireDataStore.getAutomaticClearOptionsFlow().test {
            assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
