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
import com.duckduckgo.privacy.config.api.AmpLinkInfo
import com.duckduckgo.privacy.config.api.AmpLinks
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.squareup.moshi.Moshi
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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

    private val mockAmpLinks: AmpLinks = mock()

    private lateinit var testee: BrokenSiteViewModel

    private val viewState: BrokenSiteViewModel.ViewState
        get() = testee.viewState.value!!

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        testee = BrokenSiteViewModel(
            mockPixel,
            mockBrokenSiteSender,
            mockAmpLinks,
            Moshi.Builder().add(JSONObjectAdapter()).build(),
        )
        testee.command.observeForever(mockCommandObserver)
    }

    @After
    fun after() {
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenInitializedThenCanSubmit() {
        assertTrue(viewState.submitAllowed)
    }

    @Test
    fun whenNoCategorySelectedThenCanSubmit() {
        selectAndAcceptCategory(-1)
        assertTrue(viewState.submitAllowed)
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

        val categoryExpected = testee.shuffledCategories[indexSelected]
        assertEquals(categoryExpected, viewState.categorySelected)
    }

    @Test
    fun whenCanSubmitBrokenSiteAndUrlNotNullAndSubmitPressedThenReportAndPixelSubmitted() {
        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            params = emptyArray(),
            errorCodes = emptyArray(),
            httpErrorCodes = "",
        )
        selectAndAcceptCategory()
        testee.onSubmitPressed("webViewVersion", "description")

        val brokenSiteExpected = BrokenSite(
            category = testee.shuffledCategories[0].key,
            description = "description",
            siteUrl = url,
            upgradeHttps = false,
            blockedTrackers = "",
            surrogates = "",
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.DESKTOP_SITE,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = "[]",
            httpErrorCodes = "",
        )

        verify(mockPixel).fire(AppPixelName.BROKEN_SITE_REPORTED, mapOf("url" to url))
        verify(mockBrokenSiteSender).submitBrokenSiteFeedback(brokenSiteExpected)
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenCanSubmitBrokenSiteAndUrlIsEmptyAndSubmitPressedThenDoNotSubmit() {
        val nullUrl = ""
        testee.setInitialBrokenSite(
            url = nullUrl,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            params = emptyArray(),
            errorCodes = emptyArray(),
            httpErrorCodes = "",
        )
        selectAndAcceptCategory()
        testee.onSubmitPressed("webViewVersion", "description")

        val brokenSiteExpected = BrokenSite(
            category = testee.shuffledCategories[0].key,
            description = "description",
            siteUrl = nullUrl,
            upgradeHttps = false,
            blockedTrackers = "",
            surrogates = "",
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.DESKTOP_SITE,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = "",
            httpErrorCodes = "",
        )

        verify(mockPixel, never()).fire(AppPixelName.BROKEN_SITE_REPORTED, mapOf("url" to nullUrl))
        verify(mockBrokenSiteSender, never()).submitBrokenSiteFeedback(brokenSiteExpected)
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenCanSubmitBrokenSiteAndLastAmpLinkIsNullAndSubmitPressedThenReportUrlAndPixelSubmitted() {
        whenever(mockAmpLinks.lastAmpLinkInfo).thenReturn(null)

        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            params = emptyArray(),
            errorCodes = emptyArray(),
            httpErrorCodes = "",
        )
        selectAndAcceptCategory()
        testee.onSubmitPressed("webViewVersion", "description")

        val brokenSiteExpected = BrokenSite(
            category = testee.shuffledCategories[0].key,
            description = "description",
            siteUrl = url,
            upgradeHttps = false,
            blockedTrackers = "",
            surrogates = "",
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.DESKTOP_SITE,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = "[]",
            httpErrorCodes = "",
        )

        verify(mockPixel).fire(AppPixelName.BROKEN_SITE_REPORTED, mapOf("url" to url))
        verify(mockBrokenSiteSender).submitBrokenSiteFeedback(brokenSiteExpected)
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenCanSubmitBrokenSiteAndUrlHasAssociatedAmpLinkAndSubmitPressedThenAmpLinkReportedAndPixelSubmitted() {
        whenever(mockAmpLinks.lastAmpLinkInfo).thenReturn(AmpLinkInfo(trackingUrl, url))

        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            params = emptyArray(),
            errorCodes = emptyArray(),
            httpErrorCodes = "",
        )
        selectAndAcceptCategory()
        testee.onSubmitPressed("webViewVersion", "description")

        val brokenSiteExpected = BrokenSite(
            category = testee.shuffledCategories[0].key,
            description = "description",
            siteUrl = trackingUrl,
            upgradeHttps = false,
            blockedTrackers = "",
            surrogates = "",
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.DESKTOP_SITE,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = "[]",
            httpErrorCodes = "",
        )

        verify(mockPixel).fire(AppPixelName.BROKEN_SITE_REPORTED, mapOf("url" to trackingUrl))
        verify(mockBrokenSiteSender).submitBrokenSiteFeedback(brokenSiteExpected)
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenCanSubmitBrokenSiteAndUrlNotNullAndSubmitPressedThenReportAndPixelSubmittedWithParams() {
        whenever(mockAmpLinks.lastAmpLinkInfo).thenReturn(AmpLinkInfo(trackingUrl, url))

        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            params = arrayOf("dashboard_highlighted_toggle"),
            errorCodes = emptyArray(),
            httpErrorCodes = "",
        )
        selectAndAcceptCategory()
        testee.onSubmitPressed("webViewVersion", "description")

        verify(mockPixel).fire(
            AppPixelName.BROKEN_SITE_REPORTED,
            mapOf(
                "url" to trackingUrl,
                "dashboard_highlighted_toggle" to true.toString(),
            ),
        )
    }

    @Test
    fun whenUrlIsDesktopThenSendDesktopParameter() {
        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            params = emptyArray(),
            errorCodes = emptyArray(),
            httpErrorCodes = "",
        )
        selectAndAcceptCategory()

        val brokenSiteExpected = testee.getBrokenSite(url, "", "")
        assertEquals(BrokenSiteViewModel.DESKTOP_SITE, brokenSiteExpected.siteType)
    }

    @Test
    fun whenUrlIsMobileThenSendMobileParameter() {
        val url = "http://m.example.com"
        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            params = emptyArray(),
            errorCodes = emptyArray(),
            httpErrorCodes = "",
        )
        selectAndAcceptCategory()

        val brokenSiteExpected = testee.getBrokenSite(url, "", "")
        assertEquals(BrokenSiteViewModel.MOBILE_SITE, brokenSiteExpected.siteType)
    }

    @Test
    fun whenGetBrokenSiteThenReturnCorrectCategory() {
        val url = "http://m.example.com"
        val categoryIndex = 0
        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            params = emptyArray(),
            errorCodes = emptyArray(),
            httpErrorCodes = "",
        )
        selectAndAcceptCategory(categoryIndex)

        val categoryExpected = testee.shuffledCategories[categoryIndex].key
        val brokenSiteExpected = testee.getBrokenSite(url, "", "")
        assertEquals(categoryExpected, brokenSiteExpected.category)
    }

    @Test
    fun whenCancelSelectionThenAssignOldIndexValue() {
        testee.setInitialBrokenSite(
            url = "",
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            params = emptyArray(),
            errorCodes = emptyArray(),
            httpErrorCodes = "",
        )
        selectAndAcceptCategory(0)
        testee.onCategoryIndexChanged(1)
        testee.onCategorySelectionCancelled()

        assertEquals(0, testee.indexSelected)
    }

    @Test
    fun whenCancelSelectionAndNoPreviousValueThenAssignMinusOne() {
        testee.setInitialBrokenSite(
            url = "",
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            params = emptyArray(),
            errorCodes = emptyArray(),
            httpErrorCodes = "",
        )
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
