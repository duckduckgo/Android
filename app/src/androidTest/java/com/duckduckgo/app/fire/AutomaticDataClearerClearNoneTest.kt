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

import androidx.test.annotation.UiThreadTest
import androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhatFragment.ClearWhatOption
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhatFragment.ClearWhatOption.CLEAR_NONE
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhenFragment.ClearWhenOption
import com.duckduckgo.app.settings.SettingsAutomaticallyClearWhenFragment.ClearWhenOption.APP_EXIT_ONLY
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.verification.VerificationMode

@RunWith(Parameterized::class)
class AutomaticDataClearerClearNoneTest(private val testCase: TestCase) {

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
                TestCase(
                    Expected(false, false),
                    Input(CLEAR_NONE, APP_EXIT_ONLY, true, true, true)
                ),

                // fresh app launch, enough time passed - no clearing
                TestCase(
                    Expected(false, false),
                    Input(CLEAR_NONE, APP_EXIT_ONLY, true, true, false)
                ),

                // fresh app launch, not enough time passed - no clearing
                TestCase(
                    Expected(false, false),
                    Input(CLEAR_NONE, APP_EXIT_ONLY, false, true, true)
                ),

                // fresh app launch, not enough time passed - no clearing
                TestCase(
                    Expected(false, false),
                    Input(CLEAR_NONE, APP_EXIT_ONLY, false, true, false)
                ),

                // not fresh app launch, enough time passed - no clearing
                TestCase(
                    Expected(false, false),
                    Input(CLEAR_NONE, APP_EXIT_ONLY, true, false, true)
                ),

                // not fresh app launch, enough time passed - no clearing
                TestCase(
                    Expected(false, false),
                    Input(CLEAR_NONE, APP_EXIT_ONLY, true, false, false)
                ),

                // not fresh app launch, enough time passed - no clearing
                TestCase(
                    Expected(false, false),
                    Input(CLEAR_NONE, APP_EXIT_ONLY, true, false, true)
                ),

                // not fresh app launch, enough time passed - no clearing
                TestCase(
                    Expected(false, false),
                    Input(CLEAR_NONE, APP_EXIT_ONLY, true, false, false)
                ),

                // not fresh app launch, not enough time passed - no clearing
                TestCase(
                    Expected(false, false),
                    Input(CLEAR_NONE, APP_EXIT_ONLY, false, false, true)
                ),

                // not fresh app launch, not enough time passed - no clearing
                TestCase(
                    Expected(false, false),
                    Input(CLEAR_NONE, APP_EXIT_ONLY, false, false, false)
                )
            )
        }
    }

    @UiThreadTest
    @Before
    fun setup() {

        testee = AutomaticDataClearer(mockSettingsDataStore, mockClearAction, mockTimeKeeper)

        whenever(mockTimeKeeper.hasEnoughTimeElapsed(any())).thenReturn(testCase.input.enoughTimePassed)
        whenever(mockSettingsDataStore.automaticallyClearWhatOption).thenReturn(testCase.input.clearWhat)
        whenever(mockSettingsDataStore.automaticallyClearWhenOption).thenReturn(testCase.input.clearWhen)
        whenever(mockSettingsDataStore.appUsedSinceLastClear).thenReturn(testCase.input.appUsedSinceLastClear)
    }

    @Test
    fun dataClearingTestsClearNone() = runOnUiThread {
        runBlocking {
            simulateLifecycle()

            verifyIfTabsCleared()
            verifyIfAllDataCleared()
            verifyIfBackgroundTimestampCleared()
        }
    }

    private suspend fun verifyIfTabsCleared() {
        verify(mockClearAction, testCase.expected.shouldClearTabs.toVerificationMode()).clearTabsAsync(any())
    }

    private suspend fun verifyIfAllDataCleared() {
        val numberOfTimesExpected = testCase.expected.shouldClearEverything.toVerificationMode()
        verify(mockClearAction, numberOfTimesExpected).clearTabsAndAllDataAsync(anyBoolean())
    }

    private fun verifyIfBackgroundTimestampCleared() {
        verify(mockSettingsDataStore).clearAppBackgroundTimestamp()
    }

    private suspend fun simulateLifecycle() {
        testee.isFreshAppLaunch = testCase.input.isFreshAppLaunch
        testee.onAppForegroundedAsync()
    }

    data class TestCase(val expected: Expected, val input: Input)

    data class Expected(val shouldClearTabs: Boolean, val shouldClearEverything: Boolean)

    data class Input(
        val clearWhat: ClearWhatOption,
        val clearWhen: ClearWhenOption,
        val enoughTimePassed: Boolean,
        val isFreshAppLaunch: Boolean,
        val appUsedSinceLastClear: Boolean
    )

    private fun Boolean.toVerificationMode(): VerificationMode {
        return if (this) {
            times(1)
        } else {
            never()
        }
    }
}
