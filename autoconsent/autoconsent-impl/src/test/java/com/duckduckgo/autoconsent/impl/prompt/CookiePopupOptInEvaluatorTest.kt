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

package com.duckduckgo.autoconsent.impl.prompt

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.onboarding.OnboardingFlowChecker
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autoconsent.impl.FakeSettingsRepository
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.promptscoordinator.api.ModalEvaluator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class CookiePopupOptInEvaluatorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val application: Application get() = RuntimeEnvironment.getApplication()
    private val feature = FakeFeatureToggleFactory.create(AutoconsentFeature::class.java)
    private val autoconsent: Autoconsent = mock()
    private val onboardingFlowChecker: OnboardingFlowChecker = mock {
        onBlocking { isOnboardingComplete() } doReturn true
    }
    private val settingsRepository = FakeSettingsRepository()

    private val testee by lazy {
        CookiePopupOptInEvaluator(
            appCoroutineScope = coroutineRule.testScope,
            applicationContext = application,
            autoconsentFeature = feature,
            dispatchers = coroutineRule.testDispatcherProvider,
            autoconsent = autoconsent,
            onboardingFlowChecker = onboardingFlowChecker,
            settingsRepository = settingsRepository,
        )
    }

    @Before
    fun setup() {
        feature.cookiePopUpPreferenceSetting().setRawStoredState(Toggle.State(enable = true))
    }

    @Test
    fun whenOnboardingNotCompleteThenSkippedAndNothingLaunched() = runTest {
        feature.cookiePopUpOptInPrompt().setRawStoredState(Toggle.State(enable = true))
        whenever(onboardingFlowChecker.isOnboardingComplete()).thenReturn(false)

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun whenPreferenceSettingDisabledThenSkippedAndNothingLaunched() = runTest {
        feature.cookiePopUpOptInPrompt().setRawStoredState(Toggle.State(enable = true))
        feature.cookiePopUpPreferenceSetting().setRawStoredState(Toggle.State(enable = false))

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun whenPromptToggleDisabledThenSkippedAndNothingLaunched() = runTest {
        feature.cookiePopUpOptInPrompt().setRawStoredState(Toggle.State(enable = false))

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun whenPromptToggleEnabledThenModalShownAndActivityLaunched() = runTest {
        feature.cookiePopUpOptInPrompt().setRawStoredState(Toggle.State(enable = true))

        assertEquals(ModalEvaluator.EvaluationResult.ModalShown, testee.evaluate())

        val launched = shadowOf(application).nextStartedActivity
        assertEquals(CookiePopupOptInActivity::class.java.name, launched.component?.className)
    }

    @Test
    fun whenNothingLeftToOptInToThenSkippedAndNothingLaunched() = runTest {
        feature.cookiePopUpOptInPrompt().setRawStoredState(Toggle.State(enable = true))
        whenever(autoconsent.isSettingEnabled()).thenReturn(true)
        whenever(autoconsent.isClickAcceptEnabled()).thenReturn(true)

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun whenProtectionOnButClickAcceptOffThenModalShown() = runTest {
        feature.cookiePopUpOptInPrompt().setRawStoredState(Toggle.State(enable = true))
        whenever(autoconsent.isSettingEnabled()).thenReturn(true)
        whenever(autoconsent.isClickAcceptEnabled()).thenReturn(false)

        assertEquals(ModalEvaluator.EvaluationResult.ModalShown, testee.evaluate())
    }

    @Test
    fun whenModalShownThenShownCountIncremented() = runTest {
        feature.cookiePopUpOptInPrompt().setRawStoredState(Toggle.State(enable = true))

        testee.evaluate()

        assertEquals(1, settingsRepository.optInPromptShownCount)
    }

    @Test
    fun whenSkippedThenShownCountNotIncremented() = runTest {
        feature.cookiePopUpOptInPrompt().setRawStoredState(Toggle.State(enable = false))

        testee.evaluate()

        assertEquals(0, settingsRepository.optInPromptShownCount)
    }

    @Test
    fun whenShownFewerThanThreeTimesThenModalShown() = runTest {
        feature.cookiePopUpOptInPrompt().setRawStoredState(Toggle.State(enable = true))
        settingsRepository.optInPromptShownCount = 2

        assertEquals(ModalEvaluator.EvaluationResult.ModalShown, testee.evaluate())
        assertEquals(3, settingsRepository.optInPromptShownCount)
    }

    @Test
    fun whenAlreadyShownThreeTimesThenSkippedAndNothingLaunched() = runTest {
        feature.cookiePopUpOptInPrompt().setRawStoredState(Toggle.State(enable = true))
        settingsRepository.optInPromptShownCount = 3

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, testee.evaluate())
        assertNull(shadowOf(application).nextStartedActivity)
        assertEquals(3, settingsRepository.optInPromptShownCount)
    }
}
