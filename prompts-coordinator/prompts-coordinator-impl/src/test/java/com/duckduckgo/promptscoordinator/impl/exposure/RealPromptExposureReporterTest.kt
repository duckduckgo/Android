/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.promptscoordinator.impl.exposure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.install.AppInstall
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@RunWith(AndroidJUnit4::class)
class RealPromptExposureReporterTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val appInstall: AppInstall = mock()
    private val pixel: Pixel = mock()

    private lateinit var testDataStoreFile: File
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var testee: RealPromptExposureReporter

    private var installAge: Duration? = 8.days

    @Before
    fun setUp() = runTest {
        whenever(appInstall.getInstallAge()).thenAnswer { installAge }

        testDataStoreFile = File.createTempFile("prompt_exposure_reporter_test", ".preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            scope = coroutinesTestRule.testScope,
            produceFile = { testDataStoreFile },
        )
        testee = RealPromptExposureReporter(
            appCoroutineScope = coroutinesTestRule.testScope,
            weekTracker = PromptExposureWeekTracker(testDataStore, appInstall),
            pixel = pixel,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
        )
    }

    @After
    fun tearDown() {
        testDataStoreFile.delete()
    }

    @Test
    fun whenPromptShownThenExposureAndShownPixelsFire() = runTest {
        testee.reportPromptShown("win_back_prompt")
        advanceUntilIdle()

        verify(pixel).fire(
            PromptExposurePixelName.PROMPT_EXPOSURE,
            mapOf("days_since_install" to "d7_13", "nth_in_week" to "1", "opens_prev_week" to "0"),
        )
        verify(pixel).fire(
            PromptExposurePixelName.PROMPT_SHOWN,
            mapOf("days_since_install" to "d7_13", "prompt_id" to "win_back_prompt"),
        )
    }

    @Test
    fun whenPromptsShownThroughTheWeekThenOrdinalsCountUpAndRestartAfterRollover() = runTest {
        repeat(2) { testee.reportPromptShown("win_back_prompt") }
        advanceUntilIdle()
        installAge = 14.days
        testee.reportPromptShown("win_back_prompt")
        advanceUntilIdle()

        verify(pixel).fire(PromptExposurePixelName.PROMPT_EXPOSURE, exposureParams(days = "d7_13", nth = "1"))
        verify(pixel).fire(PromptExposurePixelName.PROMPT_EXPOSURE, exposureParams(days = "d7_13", nth = "2"))
        verify(pixel).fire(PromptExposurePixelName.PROMPT_EXPOSURE, exposureParams(days = "d14_20", nth = "1"))
    }

    @Test
    fun whenPromptIdIsUnregisteredThenItIsSentAsOther() = runTest {
        testee.reportPromptShown("brand_new_evaluator")
        advanceUntilIdle()

        verify(pixel).fire(
            PromptExposurePixelName.PROMPT_SHOWN,
            mapOf("days_since_install" to "d7_13", "prompt_id" to "other"),
        )
    }

    @Test
    fun whenNtpCardShownTwiceThenItIsReportedOnceAsRemoteMessageCard() = runTest {
        repeat(2) { testee.reportNewTabPageCardShown("message") }
        advanceUntilIdle()

        verify(pixel).fire(PromptExposurePixelName.PROMPT_EXPOSURE, exposureParams(days = "d7_13", nth = "1"))
        verify(pixel, never()).fire(PromptExposurePixelName.PROMPT_EXPOSURE, exposureParams(days = "d7_13", nth = "2"))
        verify(pixel).fire(
            PromptExposurePixelName.PROMPT_SHOWN,
            mapOf("days_since_install" to "d7_13", "prompt_id" to "remote_message_card"),
        )
    }

    @Test
    fun whenInstallAgeUnknownThenNoPixelFires() = runTest {
        installAge = null

        testee.reportPromptShown("win_back_prompt")
        testee.reportNewTabPageCardShown("message")
        advanceUntilIdle()

        verifyNoInteractions(pixel)
    }

    private fun advanceUntilIdle() = coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

    private fun exposureParams(days: String, nth: String) =
        mapOf("days_since_install" to days, "nth_in_week" to nth, "opens_prev_week" to "0")
}
