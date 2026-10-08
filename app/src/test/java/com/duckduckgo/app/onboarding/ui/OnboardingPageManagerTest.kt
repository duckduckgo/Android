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
import com.duckduckgo.app.onboarding.ui.page.DefaultBrowserPage
import com.duckduckgo.app.onboarding.ui.page.welcome.WelcomePageFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OnboardingPageManagerTest {

    private lateinit var testee: OnboardingPageManager
    private val onboardingPageBuilder: OnboardingPageBuilder = mock()
    private val mockDefaultBrowserDetector: DefaultBrowserDetector = mock()
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog = mock()
    private val welcomePage: WelcomePageFragment = mock()
    private val brandDesignDefaultBrowserPage: DefaultBrowserPage = mock()

    @Before
    fun setup() {
        testee = OnboardingPageManagerWithTrackerBlocking(
            defaultRoleBrowserDialog,
            onboardingPageBuilder,
            mockDefaultBrowserDetector,
        )
    }

    @Test
    fun whenBuildPageBlueprintsAndDDGIsNotDefaultBrowserThenExpectedPagesAreTwo() {
        configureDeviceSupportsDefaultBrowser()
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(false)
        whenever(onboardingPageBuilder.buildWelcomePage())
            .thenReturn(welcomePage)
        whenever(onboardingPageBuilder.buildDefaultBrowserPage())
            .thenReturn(brandDesignDefaultBrowserPage)

        testee.buildPageBlueprints()

        assertEquals(2, testee.pageCount())
        assertTrue(testee.buildPage(0) is WelcomePageFragment)
        assertTrue(testee.buildPage(1) is DefaultBrowserPage)
    }

    @Test
    fun whenBuildPageBlueprintsAndDDGIsDefaultBrowserThenSinglePage() {
        configureDeviceSupportsDefaultBrowser()
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(false)
        whenever(onboardingPageBuilder.buildWelcomePage())
            .thenReturn(welcomePage)

        testee.buildPageBlueprints()

        assertEquals(1, testee.pageCount())
        assertTrue(testee.buildPage(0) is WelcomePageFragment)
    }

    @Test
    fun whenBuildPageBlueprintsAndShouldShowRoleDialogThenSinglePage() {
        configureDeviceSupportsDefaultBrowser()
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
        whenever(onboardingPageBuilder.buildWelcomePage())
            .thenReturn(welcomePage)

        testee.buildPageBlueprints()

        assertEquals(1, testee.pageCount())
        assertTrue(testee.buildPage(0) is WelcomePageFragment)
    }

    @Test
    fun whenBuildPageBlueprintsAndDeviceUnsupportedThenSinglePage() {
        configureDeviceDoesNotSupportDefaultBrowser()
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(false)
        whenever(onboardingPageBuilder.buildWelcomePage())
            .thenReturn(welcomePage)

        testee.buildPageBlueprints()

        assertEquals(1, testee.pageCount())
        assertTrue(testee.buildPage(0) is WelcomePageFragment)
    }

    @Test
    fun whenBuildPagePositionIsPastEndThenReturnsNull() {
        configureDeviceDoesNotSupportDefaultBrowser()
        whenever(onboardingPageBuilder.buildWelcomePage())
            .thenReturn(welcomePage)

        testee.buildPageBlueprints()

        assertNull(testee.buildPage(1))
    }

    @Test
    fun whenBuildMethodsAreCalledThenPageCountReflectsLastCall() {
        configureDeviceSupportsDefaultBrowser()
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(false)

        testee.buildPageBlueprints()
        assertEquals(2, testee.pageCount())

        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        testee.buildPageBlueprints()
        assertEquals(1, testee.pageCount())
    }

    private fun configureDeviceSupportsDefaultBrowser() {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(true)
    }

    private fun configureDeviceDoesNotSupportDefaultBrowser() {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(false)
    }
}
