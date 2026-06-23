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

package com.duckduckgo.app.onboarding

import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Locale

class DuckAiOnboardingAvailabilityTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val toggles: ExtendedOnboardingFeatureToggles = FakeFeatureToggleFactory.create(ExtendedOnboardingFeatureToggles::class.java)
    private val browserConfig: AndroidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private val mockDuckChat: DuckChat = mock()
    private val appBuildConfig: AppBuildConfig = mock { on { deviceLocale } doReturn Locale.US }

    private val testee = RealDuckAiOnboardingAvailability(
        toggles = toggles,
        duckChat = mockDuckChat,
        browserConfig = browserConfig,
        appBuildConfig = appBuildConfig,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenAllConditionsTrueThenEnabled() = runTest {
        whenever(mockDuckChat.isEnabled()).thenReturn(true)
        browserConfig.singleTabFireDialog().setRawStoredState(Toggle.State(remoteEnableState = true))
        toggles.duckAiOnboarding().setRawStoredState(Toggle.State(remoteEnableState = true))

        assertTrue(testee.isDuckAiOnboardingEnabled())
    }

    @Test
    fun whenDuckChatDisabledThenNotEnabled() = runTest {
        whenever(mockDuckChat.isEnabled()).thenReturn(false)
        browserConfig.singleTabFireDialog().setRawStoredState(Toggle.State(remoteEnableState = true))
        toggles.duckAiOnboarding().setRawStoredState(Toggle.State(remoteEnableState = true))

        assertFalse(testee.isDuckAiOnboardingEnabled())
    }

    @Test
    fun whenSingleTabFireDialogDisabledThenNotEnabled() = runTest {
        whenever(mockDuckChat.isEnabled()).thenReturn(true)
        browserConfig.singleTabFireDialog().setRawStoredState(Toggle.State(remoteEnableState = false))
        toggles.duckAiOnboarding().setRawStoredState(Toggle.State(remoteEnableState = true))

        assertFalse(testee.isDuckAiOnboardingEnabled())
    }

    @Test
    fun whenDuckAiOnboardingToggleDisabledThenNotEnabled() = runTest {
        whenever(mockDuckChat.isEnabled()).thenReturn(true)
        browserConfig.singleTabFireDialog().setRawStoredState(Toggle.State(remoteEnableState = true))
        toggles.duckAiOnboarding().setRawStoredState(Toggle.State(remoteEnableState = false))

        assertFalse(testee.isDuckAiOnboardingEnabled())
    }

    @Test
    fun whenDeviceLanguageHasIncompleteTranslationsThenNotEnabled() = runTest {
        whenever(mockDuckChat.isEnabled()).thenReturn(true)
        browserConfig.singleTabFireDialog().setRawStoredState(Toggle.State(remoteEnableState = true))
        toggles.duckAiOnboarding().setRawStoredState(Toggle.State(remoteEnableState = true))
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale("pl"))

        assertFalse(testee.isDuckAiOnboardingEnabled())
    }

    @Test
    fun whenDeviceLanguageHasCompleteTranslationsThenEnabled() = runTest {
        whenever(mockDuckChat.isEnabled()).thenReturn(true)
        browserConfig.singleTabFireDialog().setRawStoredState(Toggle.State(remoteEnableState = true))
        toggles.duckAiOnboarding().setRawStoredState(Toggle.State(remoteEnableState = true))
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale("fr"))

        assertTrue(testee.isDuckAiOnboardingEnabled())
    }

    @Test
    fun whenDeviceLanguageIsNorwegianNynorskThenNotEnabled() = runTest {
        // nn (Nynorsk) can resolve to the incomplete values-nb folder via the Norwegian macrolanguage.
        whenever(mockDuckChat.isEnabled()).thenReturn(true)
        browserConfig.singleTabFireDialog().setRawStoredState(Toggle.State(remoteEnableState = true))
        toggles.duckAiOnboarding().setRawStoredState(Toggle.State(remoteEnableState = true))
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale("nn"))

        assertFalse(testee.isDuckAiOnboardingEnabled())
    }
}
