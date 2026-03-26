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
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

@RunWith(AndroidJUnit4::class)
class FireDataStoreTest {
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testDataStoreFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var fireDataStore: FireDataStore

    @Before
    fun setup() {
        testDataStoreFile = File.createTempFile("fire_clearing_preferences_test", ".preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = coroutinesTestRule.testScope,
            produceFile = { testDataStoreFile },
        )
        settingsDataStore = mock()
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_NONE)
        whenever(settingsDataStore.automaticallyClearWhenOption).thenReturn(ClearWhenOption.APP_EXIT_ONLY)
        fireDataStore = SharedPreferencesFireDataStore(dataStore, settingsDataStore, coroutinesTestRule.testDispatcherProvider)
    }

    @After
    fun tearDown() {
        testDataStoreFile.delete()
    }

    @Test
    fun whenNoManualOptionsSet_thenReturnsDefaultOptions() = runTest {
        val options = fireDataStore.getManualClearOptions()
        assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA), options)
    }

    @Test
    fun whenManualOptionsCleared_thenReturnsEmptySet() = runTest {
        fireDataStore.setManualClearOptions(setOf(FireClearOption.TABS))

        fireDataStore.setManualClearOptions(emptySet())

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
            assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA), awaitItem())

            fireDataStore.setManualClearOptions(setOf(FireClearOption.TABS))
            assertEquals(setOf(FireClearOption.TABS), awaitItem())

            fireDataStore.addManualClearOption(FireClearOption.DATA)
            assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA), awaitItem())

            fireDataStore.removeManualClearOption(FireClearOption.TABS)
            assertEquals(setOf(FireClearOption.DATA), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNoManualOptionsSet_andClearDuckAiDataEnabled_thenReturnsDefaultOptionsWithDuckAiChats() = runTest {
        whenever(settingsDataStore.clearDuckAiData).thenReturn(true)

        val options = fireDataStore.getManualClearOptions()

        assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS), options)
    }

    @Test
    fun whenNoManualOptionsSet_andClearDuckAiDataEnabled_thenFlowEmitsDefaultOptionsWithDuckAiChats() = runTest {
        whenever(settingsDataStore.clearDuckAiData).thenReturn(true)

        fireDataStore.getManualClearOptionsFlow().test {
            assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenManualOptionsAlreadySet_andClearDuckAiDataEnabled_thenLegacySettingIgnored() = runTest {
        whenever(settingsDataStore.clearDuckAiData).thenReturn(true)

        fireDataStore.setManualClearOptions(setOf(FireClearOption.TABS))

        val options = fireDataStore.getManualClearOptions()

        assertEquals(setOf(FireClearOption.TABS), options)
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
            assertEquals(emptySet<FireClearOption>(), awaitItem())

            fireDataStore.setAutomaticClearOptions(setOf(FireClearOption.TABS))
            assertEquals(setOf(FireClearOption.TABS), awaitItem())

            fireDataStore.addAutomaticClearOption(FireClearOption.DATA)
            assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA), awaitItem())

            fireDataStore.removeAutomaticClearOption(FireClearOption.TABS)
            assertEquals(setOf(FireClearOption.DATA), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenBothSetsConfigured_thenTheyAreIndependent() = runTest {
        fireDataStore.setManualClearOptions(setOf(FireClearOption.TABS))
        fireDataStore.setAutomaticClearOptions(setOf(FireClearOption.DATA, FireClearOption.DUCKAI_CHATS))

        val manual = fireDataStore.getManualClearOptions()
        val automatic = fireDataStore.getAutomaticClearOptions()

        assertEquals(setOf(FireClearOption.TABS), manual)
        assertEquals(setOf(FireClearOption.DATA, FireClearOption.DUCKAI_CHATS), automatic)
    }

    @Test
    fun whenManualCleared_thenAutomaticUnaffected() = runTest {
        fireDataStore.setManualClearOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))
        fireDataStore.setAutomaticClearOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))

        fireDataStore.setManualClearOptions(emptySet())

        val manual = fireDataStore.getManualClearOptions()
        val automatic = fireDataStore.getAutomaticClearOptions()

        assertTrue(manual.isEmpty())
        assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA), automatic)
    }

    @Test
    fun whenAutomaticCleared_thenManualUnaffected() = runTest {
        fireDataStore.setManualClearOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))
        fireDataStore.setAutomaticClearOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))

        fireDataStore.setAutomaticClearOptions(emptySet())

        val manual = fireDataStore.getManualClearOptions()
        val automatic = fireDataStore.getAutomaticClearOptions()

        assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA), manual)
        assertTrue(automatic.isEmpty())
    }

    @Test
    fun whenNoAutomaticOptionsSet_andLegacyIsNone_thenReturnsEmptySet() = runTest {
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_NONE)

        val options = fireDataStore.getAutomaticClearOptions()

        assertTrue(options.isEmpty())
    }

    @Test
    fun whenNoAutomaticOptionsSet_andLegacyIsTabsOnly_thenReturnsTabsOnly() = runTest {
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_TABS_ONLY)

        val options = fireDataStore.getAutomaticClearOptions()

        assertEquals(setOf(FireClearOption.TABS), options)
    }

    @Test
    fun whenNoAutomaticOptionsSet_andLegacyIsTabsAndData_thenReturnsTabsAndData() = runTest {
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_TABS_AND_DATA)

        val options = fireDataStore.getAutomaticClearOptions()

        assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA), options)
    }

    @Test
    fun whenAutomaticOptionsAlreadySet_thenLegacyOptionsIgnored() = runTest {
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_TABS_AND_DATA)

        fireDataStore.setAutomaticClearOptions(setOf(FireClearOption.DUCKAI_CHATS))

        val options = fireDataStore.getAutomaticClearOptions()

        assertEquals(setOf(FireClearOption.DUCKAI_CHATS), options)
    }

    @Test
    fun whenNoAutomaticOptionsSet_andLegacyIsTabsOnly_thenFlowEmitsTabsOnly() = runTest {
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_TABS_ONLY)

        fireDataStore.getAutomaticClearOptionsFlow().test {
            assertEquals(setOf(FireClearOption.TABS), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNoAutomaticOptionsSet_andLegacyIsTabsAndData_thenFlowEmitsTabsAndData() = runTest {
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_TABS_AND_DATA)

        fireDataStore.getAutomaticClearOptionsFlow().test {
            assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNoAutomaticOptionsSet_andLegacyIsNone_andClearDuckAiDataEnabled_thenReturnsDuckAiChatsOnly() = runTest {
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_NONE)
        whenever(settingsDataStore.clearDuckAiData).thenReturn(true)

        val options = fireDataStore.getAutomaticClearOptions()

        assertEquals(setOf(FireClearOption.DUCKAI_CHATS), options)
    }

    @Test
    fun whenNoAutomaticOptionsSet_andLegacyIsTabsOnly_andClearDuckAiDataEnabled_thenReturnsTabsAndDuckAiChats() = runTest {
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_TABS_ONLY)
        whenever(settingsDataStore.clearDuckAiData).thenReturn(true)

        val options = fireDataStore.getAutomaticClearOptions()

        assertEquals(setOf(FireClearOption.TABS, FireClearOption.DUCKAI_CHATS), options)
    }

    @Test
    fun whenNoAutomaticOptionsSet_andLegacyIsTabsAndData_andClearDuckAiDataEnabled_thenReturnsAllOptions() = runTest {
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_TABS_AND_DATA)
        whenever(settingsDataStore.clearDuckAiData).thenReturn(true)

        val options = fireDataStore.getAutomaticClearOptions()

        assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS), options)
    }

    @Test
    fun whenNoAutomaticOptionsSet_andLegacyIsTabsAndData_andClearDuckAiDataEnabled_thenFlowEmitsAllOptions() = runTest {
        whenever(settingsDataStore.automaticallyClearWhatOption).thenReturn(ClearWhatOption.CLEAR_TABS_AND_DATA)
        whenever(settingsDataStore.clearDuckAiData).thenReturn(true)

        fireDataStore.getAutomaticClearOptionsFlow().test {
            assertEquals(setOf(FireClearOption.TABS, FireClearOption.DATA, FireClearOption.DUCKAI_CHATS), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNoWhenOptionSet_thenReturnsLegacyDefault() = runTest {
        val option = fireDataStore.getAutomaticallyClearWhenOption()
        assertEquals(ClearWhenOption.APP_EXIT_ONLY, option)
    }

    @Test
    fun whenWhenOptionSet_thenReturnsCorrectOption() = runTest {
        fireDataStore.setAutomaticallyClearWhenOption(ClearWhenOption.APP_EXIT_OR_5_MINS)

        val option = fireDataStore.getAutomaticallyClearWhenOption()
        assertEquals(ClearWhenOption.APP_EXIT_OR_5_MINS, option)
    }

    @Test
    fun whenWhenOptionSetToAppExitOnly_thenReturnsAppExitOnly() = runTest {
        fireDataStore.setAutomaticallyClearWhenOption(ClearWhenOption.APP_EXIT_ONLY)

        val option = fireDataStore.getAutomaticallyClearWhenOption()
        assertEquals(ClearWhenOption.APP_EXIT_ONLY, option)
    }

    @Test
    fun whenWhenOptionSetTo15Mins_thenReturns15Mins() = runTest {
        fireDataStore.setAutomaticallyClearWhenOption(ClearWhenOption.APP_EXIT_OR_15_MINS)

        val option = fireDataStore.getAutomaticallyClearWhenOption()
        assertEquals(ClearWhenOption.APP_EXIT_OR_15_MINS, option)
    }

    @Test
    fun whenWhenOptionSetTo30Mins_thenReturns30Mins() = runTest {
        fireDataStore.setAutomaticallyClearWhenOption(ClearWhenOption.APP_EXIT_OR_30_MINS)

        val option = fireDataStore.getAutomaticallyClearWhenOption()
        assertEquals(ClearWhenOption.APP_EXIT_OR_30_MINS, option)
    }

    @Test
    fun whenWhenOptionSetTo60Mins_thenReturns60Mins() = runTest {
        fireDataStore.setAutomaticallyClearWhenOption(ClearWhenOption.APP_EXIT_OR_60_MINS)

        val option = fireDataStore.getAutomaticallyClearWhenOption()
        assertEquals(ClearWhenOption.APP_EXIT_OR_60_MINS, option)
    }

    @Test
    fun whenWhenOptionChanged_thenFlowEmitsNewValues() = runTest {
        fireDataStore.getAutomaticallyClearWhenOptionFlow().test {
            assertEquals(ClearWhenOption.APP_EXIT_ONLY, awaitItem())

            fireDataStore.setAutomaticallyClearWhenOption(ClearWhenOption.APP_EXIT_OR_5_MINS)
            assertEquals(ClearWhenOption.APP_EXIT_OR_5_MINS, awaitItem())

            fireDataStore.setAutomaticallyClearWhenOption(ClearWhenOption.APP_EXIT_OR_15_MINS)
            assertEquals(ClearWhenOption.APP_EXIT_OR_15_MINS, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNoWhenOptionSet_andLegacyIs5Mins_thenReturns5Mins() = runTest {
        whenever(settingsDataStore.automaticallyClearWhenOption).thenReturn(ClearWhenOption.APP_EXIT_OR_5_MINS)

        val option = fireDataStore.getAutomaticallyClearWhenOption()
        assertEquals(ClearWhenOption.APP_EXIT_OR_5_MINS, option)
    }

    @Test
    fun whenNoWhenOptionSet_andLegacyIs15Mins_thenFlowEmits15Mins() = runTest {
        whenever(settingsDataStore.automaticallyClearWhenOption).thenReturn(ClearWhenOption.APP_EXIT_OR_15_MINS)

        fireDataStore.getAutomaticallyClearWhenOptionFlow().test {
            assertEquals(ClearWhenOption.APP_EXIT_OR_15_MINS, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenWhenOptionAlreadySet_thenLegacyOptionIgnored() = runTest {
        whenever(settingsDataStore.automaticallyClearWhenOption).thenReturn(ClearWhenOption.APP_EXIT_OR_30_MINS)

        fireDataStore.setAutomaticallyClearWhenOption(ClearWhenOption.APP_EXIT_OR_5_MINS)

        val option = fireDataStore.getAutomaticallyClearWhenOption()
        assertEquals(ClearWhenOption.APP_EXIT_OR_5_MINS, option)
    }
}
