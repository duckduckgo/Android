/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.fire

import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhatFragment.ClearWhatOption
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhatFragment.ClearWhatOption.*
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhenFragment.ClearWhenOption
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhenFragment.ClearWhenOption.*
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.nhaarman.mockito_kotlin.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.verification.VerificationMode

@RunWith(Parameterized::class)
class AutomaticDataClearerTest(private val testCase: TestCase) {

    private lateinit var testee: AutomaticDataClearer

    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockClearAction: ClearDataAction = mock()
    private val mockTimeKeeper: BackgroundTimeKeeper = mock()

    companion object {

        @JvmStatic
        @Parameters(name = "Test case: {index} - {0}")
        fun testData(): Array<TestCase> {

            return arrayOf(

                /* Clear None */

                // fresh app launch, enough time passed - no clearing
                TestCase(Expected(false, false), Input(CLEAR_NONE, APP_EXIT_ONLY, true, true)),

                // fresh app launch, not enough time passed - no clearing
                TestCase(Expected(false, false), Input(CLEAR_NONE, APP_EXIT_ONLY, false, true)),

                // not fresh app launch, enough time passed - no clearing
                TestCase(Expected(false, false), Input(CLEAR_NONE, APP_EXIT_ONLY, true, false)),

                // not fresh app launch, not enough time passed - no clearing
                TestCase(Expected(false, false), Input(CLEAR_NONE, APP_EXIT_ONLY, false, false)),


                /* Clear Tabs */

                // fresh app launch, enough time passed - should clear tabs
                TestCase(Expected(true, false), Input(CLEAR_TABS_ONLY, APP_EXIT_ONLY, true, true)),

                // fresh app launch, not enough time passed - should clear tabs
                TestCase(Expected(true, false), Input(CLEAR_TABS_ONLY, APP_EXIT_ONLY, false, true)),

                // not fresh app launch, enough time passed - no clearing
                TestCase(Expected(false, false), Input(CLEAR_TABS_ONLY, APP_EXIT_ONLY, true, false)),

                // not fresh app launch, not enough time passed - no clearing
                TestCase(Expected(false, false), Input(CLEAR_TABS_ONLY, APP_EXIT_ONLY, false, false)),

                // not app exit only - enough time passed - should clear tabs
                TestCase(Expected(true, false), Input(CLEAR_TABS_ONLY, APP_EXIT_OR_5_MINS, true, false)),

                // not app exit only - not enough time passed - no clearing
                TestCase(Expected(false, false), Input(CLEAR_TABS_ONLY, APP_EXIT_OR_5_MINS, false, false)),


                /* Clear everything */

                // fresh app launch, enough time passed - should clear everything
                TestCase(Expected(false, true), Input(CLEAR_TABS_AND_DATA, APP_EXIT_ONLY, true, true)),

                // fresh app launch, not enough time passed - should clear everything
                TestCase(Expected(false, true), Input(CLEAR_TABS_AND_DATA, APP_EXIT_ONLY, false, true)),

                // not fresh app launch, enough time passed - no clearing
                TestCase(Expected(false, false), Input(CLEAR_TABS_AND_DATA, APP_EXIT_ONLY, true, false)),

                // not fresh app launch, not enough time passed - no clearing
                TestCase(Expected(false, false), Input(CLEAR_TABS_AND_DATA, APP_EXIT_ONLY, false, false)),

                // not app exit only - enough time passed - should clear everything
                TestCase(Expected(false, true), Input(CLEAR_TABS_AND_DATA, APP_EXIT_OR_5_MINS, true, false)),

                // not app exit only - not enough time passed - no clearing
                TestCase(Expected(false, false), Input(CLEAR_TABS_AND_DATA, APP_EXIT_OR_5_MINS, false, false)),

                // fresh app launch - should not restart process
                TestCase(Expected(false, true, false), Input(CLEAR_TABS_AND_DATA, APP_EXIT_OR_15_MINS, false, true)),

                // not fresh app launch, enough time passed - should restart process
                TestCase(Expected(false, true, true), Input(CLEAR_TABS_AND_DATA, APP_EXIT_OR_15_MINS, true, false))
            )
        }
    }

    @Before
    fun setup() {

        testee = AutomaticDataClearer(mockSettingsDataStore, mockClearAction, mockTimeKeeper)

        whenever(mockTimeKeeper.hasEnoughTimeElapsed(any())).thenReturn(testCase.input.enoughTimePassed)
        whenever(mockSettingsDataStore.automaticallyClearWhatOption).thenReturn(testCase.input.clearWhat)
        whenever(mockSettingsDataStore.automaticallyClearWhenOption).thenReturn(testCase.input.clearWhen)

    }

    @Test
    fun dataClearingTests() {
        simulateLifecycle()

        verifyIfTabsCleared()
        verifyIfAllDataCleared()
        verifyIfBackgroundTimestampCleared()
    }

    private fun verifyIfTabsCleared() {
        verify(mockClearAction, testCase.expected.shouldClearTabs.toVerificationMode()).clearTabs()
    }

    private fun verifyIfAllDataCleared() {
        val numberOfTimesExpected = testCase.expected.shouldClearEverything.toVerificationMode()
        val restartProcessExpected = testCase.expected.shouldRestartProcess

        if (restartProcessExpected == null) {
            verify(mockClearAction, numberOfTimesExpected).clearEverything(anyBoolean())
        } else {
            verify(mockClearAction, numberOfTimesExpected).clearEverything(restartProcessExpected)
        }
    }

    private fun verifyIfBackgroundTimestampCleared() {
        verify(mockSettingsDataStore).clearAppBackgroundTimestamp()
    }


    private fun simulateLifecycle() {
        if (testCase.input.isFreshAppLaunch) {
            testee.onAppCreated()
        }
        testee.onAppForegrounded()
    }

    data class TestCase(
        val expected: Expected,
        val input: Input
    )

    data class Expected(
        val shouldClearTabs: Boolean,
        val shouldClearEverything: Boolean,
        val shouldRestartProcess: Boolean? = null
    )

    data class Input(
        val clearWhat: ClearWhatOption,
        val clearWhen: ClearWhenOption,
        val enoughTimePassed: Boolean,
        val isFreshAppLaunch: Boolean
    )

    private fun Boolean.toVerificationMode(): VerificationMode {
        return if (this) {
            times(1)
        } else {
            never()
        }
    }
}
