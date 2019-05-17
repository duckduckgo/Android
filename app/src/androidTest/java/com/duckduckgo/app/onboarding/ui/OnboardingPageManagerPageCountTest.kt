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

import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.VariantFeature
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class OnboardingPageManagerPageCountTest(private val testCase: TestCase) {

    private lateinit var testee: OnboardingPageManager
    private val variantManager: VariantManager = mock()
    private val onboardingPageBuilder: OnboardingPageBuilder = mock()
    private val mockDefaultBrowserDetector: DefaultBrowserDetector = mock()

    @Before
    fun setup() {
        testee = OnboardingPageManagerWithTrackerBlocking(variantManager, onboardingPageBuilder, mockDefaultBrowserDetector)
    }

    @Test
    fun ensurePageCountAsExpected() {
        configureDefaultBrowserPageConfig()
        configureTrackerBlockerOptInPageConfig()

        testee.buildPageBlueprints()
        assertEquals(testCase.expectedPageCount, testee.pageCount())
    }

    private fun configureTrackerBlockerOptInPageConfig() {
        if (testCase.trackerBlockerOptInPage) {
            configureShowTrackerBlockerOptInPage()
        } else {
            configureHideTrackerBlockerOptInPage()
        }
    }

    private fun configureDefaultBrowserPageConfig() {
        if (testCase.defaultBrowserPage) {
            configureDeviceSupportsDefaultBrowser()
        } else {
            configureDeviceDoesNotSupportDefaultBrowser()
        }
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "Test case: {index} - {0}")
        fun testData(): Array<TestCase> {
            return arrayOf(
                TestCase(false, false, 1),
                TestCase(false, true, 2),
                TestCase(true, false, 2),
                TestCase(true, true, 3)
            )
        }
    }

    private fun configureDeviceSupportsDefaultBrowser() {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(true)
    }

    private fun configureDeviceDoesNotSupportDefaultBrowser() {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(false)
    }

    private fun configureShowTrackerBlockerOptInPage() {
        whenever(variantManager.getVariant()).thenReturn(Variant("test", features = listOf(VariantFeature.TrackerBlockingOnboardingOptIn)))
    }

    private fun configureHideTrackerBlockerOptInPage() {
        whenever(variantManager.getVariant()).thenReturn(Variant("test", features = emptyList()))
    }

    data class TestCase(val trackerBlockerOptInPage: Boolean, val defaultBrowserPage: Boolean, val expectedPageCount: Int)
}