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
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.statistics.Variant
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
    private val onboardingPageBuilder: OnboardingPageBuilder = mock()
    private val mockDefaultBrowserDetector: DefaultBrowserDetector = mock()
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog = mock()

    @Before
    fun setup() {
        testee = OnboardingPageManagerWithTrackerBlocking(defaultRoleBrowserDialog, onboardingPageBuilder, mockDefaultBrowserDetector)
    }

    @Test
    fun ensurePageCountAsExpected() {
        configureDefaultBrowserPageConfig()

        testee.buildPageBlueprints()
        assertEquals(testCase.expectedPageCount, testee.pageCount())
    }

    private fun configureDefaultBrowserPageConfig() {
        if (testCase.defaultBrowserPage) {
            configureDeviceSupportsDefaultBrowser()
        } else {
            configureDeviceDoesNotSupportDefaultBrowser()
        }
    }

    companion object {

        private val otherVariant = Variant(key = "variant", features = listOf(), filterBy = { true })

        @JvmStatic
        @Parameterized.Parameters(name = "Test case: {index} - {0}")
        fun testData(): Array<TestCase> {
            return arrayOf(
                TestCase(false, 1, otherVariant),
                TestCase(true, 2, otherVariant)
            )
        }
    }

    private fun configureDeviceSupportsDefaultBrowser() {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(true)
    }

    private fun configureDeviceDoesNotSupportDefaultBrowser() {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(false)
    }

    data class TestCase(val defaultBrowserPage: Boolean, val expectedPageCount: Int, val variant: Variant)
}
