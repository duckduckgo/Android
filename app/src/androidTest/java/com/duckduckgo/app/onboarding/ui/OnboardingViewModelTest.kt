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

package com.duckduckgo.app.onboarding.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test


class OnboardingViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private var onboardingStore: OnboardingStore = mock()
    private var mockDefaultBrowserDetector: DefaultBrowserDetector = mock()

    private val testee: OnboardingViewModel by lazy {
        OnboardingViewModel(onboardingStore, mockDefaultBrowserDetector)
    }

    @Test
    fun whenOnboardingDoneThenStoreNotifiedThatOnboardingShown() {
        verify(onboardingStore, never()).onboardingShown()
        testee.onOnboardingDone()
        verify(onboardingStore).onboardingShown()
    }

    @Test
    fun whenFreshInstallFirstPageRequestedThenUnifiedWelcomePageReturned() {
        val page = testee.getItem(0, isFreshAppInstall = true)
        assertTrue(page is OnboardingPageFragment.UnifiedWelcomePage)
    }

    @Test
    fun whenNotFreshInstallFirstPageRequestedThenUnifiedWelcomePageReturned() {
        val page = testee.getItem(0, isFreshAppInstall = false)
        assertTrue(page is OnboardingPageFragment.UnifiedWelcomePage)
    }

    @Test
    fun whenFreshInstallSecondPageRequestedWithDefaultBrowserCapableThenDefaultBrowserPageReturned() {
        configureDeviceSupportsDefaultBrowser()
        val page = testee.getItem(1, isFreshAppInstall = true)
        assertTrue(page is OnboardingPageFragment.DefaultBrowserPage)
    }

    @Test
    fun whenNotFreshInstallSecondPageRequestedWithDefaultBrowserCapableThenNoPageReturned() {
        configureDeviceSupportsDefaultBrowser()
        val page = testee.getItem(1, isFreshAppInstall = false)
        assertNull(page)
    }

    @Test
    fun whenFreshInstallSecondPageRequestedButDefaultBrowserNotCapableThenNoPageReturned() {
        configureDeviceDoesNotSupportDefaultBrowser()
        val page = testee.getItem(1, isFreshAppInstall = true)
        assertNull(page)
    }

    @Test
    fun whenNotFreshInstallSecondPageRequestedButDefaultBrowserNotCapableThenNoPageReturned() {
        configureDeviceDoesNotSupportDefaultBrowser()
        val page = testee.getItem(1, isFreshAppInstall = false)
        assertNull(page)
    }

    @Test
    fun whenDefaultBrowserSupportedThenFirstPageShowsContinueTextOnButton() {
        configureDeviceSupportsDefaultBrowser()
        val resourceId = testee.getContinueButtonTextResourceId(0, isFreshAppInstall = true)
        assertEquals(R.string.onboardingContinue, resourceId)
    }

    @Test
    fun whenFreshInstallDefaultBrowserNotSupportedThenFirstPageShowsFinalTextOnButton() {
        configureDeviceDoesNotSupportDefaultBrowser()
        val resourceId = testee.getContinueButtonTextResourceId(0, isFreshAppInstall = true)
        assertEquals(R.string.onboardingContinueFinalPage, resourceId)
    }

    @Test
    fun whenNotFreshInstallDefaultBrowserSupportedThenFirstPageShowsBackTextOnButton() {
        configureDeviceSupportsDefaultBrowser()
        val resourceId = testee.getContinueButtonTextResourceId(0, isFreshAppInstall = false)
        assertEquals(R.string.onboardingBackButton, resourceId)
    }

    @Test
    fun whenNotFreshInstallDefaultBrowserNotSupportedThenFirstPageShowsBackTextOnButton() {
        configureDeviceDoesNotSupportDefaultBrowser()
        val resourceId = testee.getContinueButtonTextResourceId(0, isFreshAppInstall = false)
        assertEquals(R.string.onboardingBackButton, resourceId)
    }

    private fun configureDeviceSupportsDefaultBrowser() {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(true)
    }

    private fun configureDeviceDoesNotSupportDefaultBrowser() {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(false)
    }

}