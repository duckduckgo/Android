/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui

import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OnboardingPageManagerTest {

    private lateinit var testee: OnboardingPageManager
    private val variantManager: VariantManager = mock()
    private val onboardingPageBuilder: OnboardingPageBuilder = mock()
    private val mockDefaultBrowserDetector: DefaultBrowserDetector = mock()

    @Before
    fun setup() {
        testee = OnboardingPageManagerWithTrackerBlocking(variantManager, onboardingPageBuilder, mockDefaultBrowserDetector)
    }

    @Test
    fun whenDefaultBrowserSupportedAndTrackerBlockingOptInSupportedThenFirstPageShowsContinueTextOnButton() {
        configureDeviceSupportsDefaultBrowser()
        configureShouldShowTrackerBlockerOptIn()
        val isFreshInstall = true
        testee.buildPageBlueprints(isFreshAppInstall = isFreshInstall)
        val resourceId = testee.getContinueButtonTextResourceId(0, isFreshAppInstall = isFreshInstall)
        assertEquals(R.string.onboardingContinue, resourceId)
    }

    @Test
    fun whenFreshInstallDefaultBrowserNotSupportedAndTrackerBlockingOptInNotSupportedThenFirstPageShowsFinalTextOnButton() {
        configureDeviceDoesNotSupportDefaultBrowser()
        configureShouldNotShowTrackerBlockerOptIn()
        val isFreshAppInstall = true
        testee.buildPageBlueprints(isFreshAppInstall)
        val resourceId = testee.getContinueButtonTextResourceId(0, isFreshAppInstall = isFreshAppInstall)
        assertEquals(R.string.onboardingContinueFinalPage, resourceId)
    }

    @Test
    fun whenNotFreshInstallDefaultBrowserSupportedThenFirstPageShowsBackTextOnButton() {
        configureDeviceSupportsDefaultBrowser()
        val isFreshAppInstall = false
        testee.buildPageBlueprints(isFreshAppInstall)
        val resourceId = testee.getContinueButtonTextResourceId(0, isFreshAppInstall = false)
        assertEquals(R.string.onboardingBackButton, resourceId)
    }

    @Test
    fun whenNotFreshInstallDefaultBrowserNotSupportedThenFirstPageShowsBackTextOnButton() {
        configureDeviceDoesNotSupportDefaultBrowser()
        val resourceId = testee.getContinueButtonTextResourceId(0, isFreshAppInstall = false)
        assertEquals(R.string.onboardingBackButton, resourceId)
    }

    private fun configureShouldNotShowTrackerBlockerOptIn() {
        whenever(variantManager.getVariant()).thenReturn(Variant("test", features = emptyList()))
    }

    private fun configureShouldShowTrackerBlockerOptIn() {
        whenever(variantManager.getVariant()).thenReturn(
            Variant(
                "test",
                features = listOf(VariantManager.VariantFeature.TrackerBlockingOnboardingOptIn)
            )
        )
    }

    private fun configureDeviceSupportsDefaultBrowser() {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(true)
    }

    private fun configureDeviceDoesNotSupportDefaultBrowser() {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(false)
    }

}