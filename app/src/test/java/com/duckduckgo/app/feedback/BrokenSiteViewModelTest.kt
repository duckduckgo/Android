/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.feedback

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.brokensite.BrokenSiteViewModel
import com.duckduckgo.app.brokensite.BrokenSiteViewModel.Command
import com.duckduckgo.app.brokensite.api.BrokenSiteSender
import com.duckduckgo.app.brokensite.model.BrokenSite
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.privacy.config.api.TrackingLinkDetector
import com.duckduckgo.privacy.config.api.TrackingLinkInfo
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class BrokenSiteViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    private val mockPixel: Pixel = mock()

    private val mockBrokenSiteSender: BrokenSiteSender = mock()

    private val mockCommandObserver: Observer<Command> = mock()

    private val mockTrackingLinkDetector: TrackingLinkDetector = mock()

    private lateinit var testee: BrokenSiteViewModel

    private val viewState: BrokenSiteViewModel.ViewState
        get() = testee.viewState.value!!

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        testee = BrokenSiteViewModel(mockPixel, mockBrokenSiteSender, mockTrackingLinkDetector)
        testee.command.observeForever(mockCommandObserver)
    }

    @After
    fun after() {
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenInitializedThenCannotSubmit() {
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenCategorySelectedThenCanSubmit() {
        selectAndAcceptCategory()
        assertTrue(viewState.submitAllowed)
    }

    @Test
    fun whenCategoryChangedButNotSelectedThenCannotSubmit() {
        testee.onCategoryIndexChanged(0)
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenNoCategorySelectedThenCannotSubmit() {
        selectAndAcceptCategory(-1)
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenCategorySelectedButNotChangedThenReturnOldCategory() {
        testee.onCategoryIndexChanged(0)
        testee.onCategoryAccepted()
        testee.onCategoryIndexChanged(1)
        assertEquals(0, viewState.indexSelected)
    }

    @Test
    fun whenCategoryAcceptedAndIncorrectIndexThenReturnNullCategory() {
        selectAndAcceptCategory(-1)
        assertNull(viewState.categorySelected)
    }

    @Test
    fun whenCategoryAcceptedAndCorrectIndexThenReturnCategory() {
        val indexSelected = 0
        selectAndAcceptCategory(indexSelected)

        val categoryExpected = testee.categories[indexSelected]
        assertEquals(categoryExpected, viewState.categorySelected)
    }

    @Test
    fun whenCanSubmitBrokenSiteAndUrlNotNullAndSubmitPressedThenReportAndPixelSubmitted() {
        testee.setInitialBrokenSite(url, "", "", upgradedHttps = false, urlParametersRemoved = false)
        selectAndAcceptCategory()
        testee.onSubmitPressed("webViewVersion")

        val brokenSiteExpected = BrokenSite(
            category = testee.categories[0].key,
            siteUrl = url,
            upgradeHttps = false,
            blockedTrackers = "",
            surrogates = "",
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.DESKTOP_SITE,
            urlParametersRemoved = false
        )

        verify(mockPixel).fire(AppPixelName.BROKEN_SITE_REPORTED, mapOf("url" to url))
        verify(mockBrokenSiteSender).submitBrokenSiteFeedback(brokenSiteExpected)
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenCanSubmitBrokenSiteAndUrlIsEmptyAndSubmitPressedThenDoNotSubmit() {
        val nullUrl = ""
        testee.setInitialBrokenSite(nullUrl, "", "", upgradedHttps = false, urlParametersRemoved = false)
        selectAndAcceptCategory()
        testee.onSubmitPressed("webViewVersion")

        val brokenSiteExpected = BrokenSite(
            category = testee.categories[0].key,
            siteUrl = nullUrl,
            upgradeHttps = false,
            blockedTrackers = "",
            surrogates = "",
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.DESKTOP_SITE,
            urlParametersRemoved = false
        )

        verify(mockPixel, never()).fire(AppPixelName.BROKEN_SITE_REPORTED, mapOf("url" to nullUrl))
        verify(mockBrokenSiteSender, never()).submitBrokenSiteFeedback(brokenSiteExpected)
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenCanSubmitBrokenSiteAndLastTrackingLinkIsNullAndSubmitPressedThenReportUrlAndPixelSubmitted() {
        whenever(mockTrackingLinkDetector.lastTrackingLinkInfo).thenReturn(null)

        testee.setInitialBrokenSite(url, "", "", upgradedHttps = false, urlParametersRemoved = false)
        selectAndAcceptCategory()
        testee.onSubmitPressed("webViewVersion")

        val brokenSiteExpected = BrokenSite(
            category = testee.categories[0].key,
            siteUrl = url,
            upgradeHttps = false,
            blockedTrackers = "",
            surrogates = "",
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.DESKTOP_SITE,
            urlParametersRemoved = false
        )

        verify(mockPixel).fire(AppPixelName.BROKEN_SITE_REPORTED, mapOf("url" to url))
        verify(mockBrokenSiteSender).submitBrokenSiteFeedback(brokenSiteExpected)
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenCanSubmitBrokenSiteAndUrlHasAssociatedTrackingLinkAndSubmitPressedThenTrackingLinkReportedAndPixelSubmitted() {
        whenever(mockTrackingLinkDetector.lastTrackingLinkInfo).thenReturn(TrackingLinkInfo(trackingUrl, url))

        testee.setInitialBrokenSite(url, "", "", upgradedHttps = false, urlParametersRemoved = false)
        selectAndAcceptCategory()
        testee.onSubmitPressed("webViewVersion")

        val brokenSiteExpected = BrokenSite(
            category = testee.categories[0].key,
            siteUrl = trackingUrl,
            upgradeHttps = false,
            blockedTrackers = "",
            surrogates = "",
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.DESKTOP_SITE,
            urlParametersRemoved = false
        )

        verify(mockPixel).fire(AppPixelName.BROKEN_SITE_REPORTED, mapOf("url" to trackingUrl))
        verify(mockBrokenSiteSender).submitBrokenSiteFeedback(brokenSiteExpected)
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenUrlIsDesktopThenSendDesktopParameter() {
        testee.setInitialBrokenSite(url, "", "", upgradedHttps = false, urlParametersRemoved = false)
        selectAndAcceptCategory()

        val brokenSiteExpected = testee.getBrokenSite(url, "")
        assertEquals(BrokenSiteViewModel.DESKTOP_SITE, brokenSiteExpected.siteType)
    }

    @Test
    fun whenUrlIsMobileThenSendMobileParameter() {
        val url = "http://m.example.com"
        testee.setInitialBrokenSite(url, "", "", upgradedHttps = false, urlParametersRemoved = false)
        selectAndAcceptCategory()

        val brokenSiteExpected = testee.getBrokenSite(url, "")
        assertEquals(BrokenSiteViewModel.MOBILE_SITE, brokenSiteExpected.siteType)
    }

    @Test
    fun whenGetBrokenSiteThenReturnCorrectCategory() {
        val url = "http://m.example.com"
        val categoryIndex = 0
        testee.setInitialBrokenSite(url, "", "", upgradedHttps = false, urlParametersRemoved = false)
        selectAndAcceptCategory(categoryIndex)

        val categoryExpected = testee.categories[categoryIndex].key
        val brokenSiteExpected = testee.getBrokenSite(url, "")
        assertEquals(categoryExpected, brokenSiteExpected.category)
    }

    @Test
    fun whenCancelSelectionThenAssignOldIndexValue() {
        testee.setInitialBrokenSite("", "", "", upgradedHttps = false, urlParametersRemoved = false)
        selectAndAcceptCategory(0)
        testee.onCategoryIndexChanged(1)
        testee.onCategorySelectionCancelled()

        assertEquals(0, testee.indexSelected)
    }

    @Test
    fun whenCancelSelectionAndNoPreviousValueThenAssignMinusOne() {
        testee.setInitialBrokenSite("", "", "", upgradedHttps = false, urlParametersRemoved = false)
        testee.onCategoryIndexChanged(1)
        testee.onCategorySelectionCancelled()

        assertEquals(-1, testee.indexSelected)
    }

    private fun selectAndAcceptCategory(indexSelected: Int = 0) {
        testee.onCategoryIndexChanged(indexSelected)
        testee.onCategoryAccepted()
    }

    companion object Constants {
        private const val url = "http://example.com"
        private const val trackingUrl = "https://foo.com"
    }
}
