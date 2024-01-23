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
import com.duckduckgo.app.brokensite.BrokenSiteViewModel
import com.duckduckgo.app.brokensite.BrokenSiteViewModel.Command
import com.duckduckgo.app.brokensite.api.BrokenSiteSender
import com.duckduckgo.app.brokensite.model.BrokenSite
import com.duckduckgo.app.brokensite.model.BrokenSiteCategory
import com.duckduckgo.app.brokensite.model.ReportFlow
import com.duckduckgo.app.brokensite.model.SiteProtectionsState
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.COUNT
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.UNIQUE
import com.duckduckgo.browser.api.brokensite.BrokenSiteData.ReportFlow.MENU
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.AmpLinkInfo
import com.duckduckgo.privacy.config.api.AmpLinks
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupExperimentPixelParamsProvider
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsToggleUsageListener
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyString
import org.mockito.Mockito.never
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
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

    private val mockFeatureToggle: FeatureToggle = mock()

    private val mockContentBlocking: ContentBlocking = mock()

    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()

    private val mockUserAllowListRepository: UserAllowListRepository = mock()

    private val mockPrivacyProtectionsToggleUsageListener: PrivacyProtectionsToggleUsageListener = mock()

    private val privacyProtectionsPopupExperimentPixelParamsProvider = FakePrivacyProtectionsPopupExperimentPixelParamsProvider()

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
            mockFeatureToggle,
            mockContentBlocking,
            mockUnprotectedTemporary,
            mockUserAllowListRepository,
            mockPrivacyProtectionsToggleUsageListener,
            privacyProtectionsPopupExperimentPixelParamsProvider,
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
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )
        selectAndAcceptCategory()
        testee.onSubmitPressed("webViewVersion", "description", "")

        val brokenSiteExpected = BrokenSite(
            category = testee.shuffledCategories[0].key,
            description = "description",
            siteUrl = url,
            upgradeHttps = false,
            blockedTrackers = "",
            surrogates = "",
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.MOBILE_SITE,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = "[]",
            httpErrorCodes = "",
            loginSite = "",
            reportFlow = ReportFlow.MENU,
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
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )
        selectAndAcceptCategory()
        testee.onSubmitPressed("webViewVersion", "description", "")

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
            loginSite = "",
            reportFlow = ReportFlow.MENU,
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
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )
        selectAndAcceptCategory()
        testee.onSubmitPressed("webViewVersion", "description", "")

        val brokenSiteExpected = BrokenSite(
            category = testee.shuffledCategories[0].key,
            description = "description",
            siteUrl = url,
            upgradeHttps = false,
            blockedTrackers = "",
            surrogates = "",
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.MOBILE_SITE,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = "[]",
            httpErrorCodes = "",
            loginSite = "",
            reportFlow = ReportFlow.MENU,
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
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )
        selectAndAcceptCategory()
        testee.onSubmitPressed("webViewVersion", "description", "")

        val brokenSiteExpected = BrokenSite(
            category = testee.shuffledCategories[0].key,
            description = "description",
            siteUrl = trackingUrl,
            upgradeHttps = false,
            blockedTrackers = "",
            surrogates = "",
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.MOBILE_SITE,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = "[]",
            httpErrorCodes = "",
            loginSite = "",
            reportFlow = ReportFlow.MENU,
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
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )
        selectAndAcceptCategory()
        testee.onSubmitPressed("webViewVersion", "description", "")

        verify(mockPixel).fire(
            AppPixelName.BROKEN_SITE_REPORTED,
            mapOf(
                "url" to trackingUrl,
            ),
        )
    }

    @Test
    fun whenIsDesktopModeTrueThenSendDesktopParameter() {
        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = true,
            reportFlow = MENU,
        )
        selectAndAcceptCategory()

        val brokenSiteExpected = testee.getBrokenSite(url, "", "", "")
        assertEquals(BrokenSiteViewModel.DESKTOP_SITE, brokenSiteExpected.siteType)
    }

    @Test
    fun whenDesktopModeIsFalseThenSendMobileParameter() {
        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )
        selectAndAcceptCategory()

        val brokenSiteExpected = testee.getBrokenSite(url, "", "", "")
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
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )
        selectAndAcceptCategory(categoryIndex)

        val categoryExpected = testee.shuffledCategories[categoryIndex].key
        val brokenSiteExpected = testee.getBrokenSite(url, "", "", "")
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
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
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
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )
        testee.onCategoryIndexChanged(1)
        testee.onCategorySelectionCancelled()

        assertEquals(-1, testee.indexSelected)
    }

    @Test
    fun whenCategoryLoginsThenUseLoginSite() {
        val categoryIndex = testee.shuffledCategories.indexOfFirst { it.key == BrokenSiteCategory.LOGIN_CATEGORY_KEY }

        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )
        selectAndAcceptCategory(categoryIndex)
        testee.onSubmitPressed("webViewVersion", "description", "test")

        val brokenSiteExpected = BrokenSite(
            category = testee.shuffledCategories[categoryIndex].key,
            description = "description",
            siteUrl = url,
            upgradeHttps = false,
            blockedTrackers = "",
            surrogates = "",
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.MOBILE_SITE,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = "[]",
            httpErrorCodes = "",
            loginSite = "test",
            reportFlow = ReportFlow.MENU,
        )

        verify(mockPixel).fire(AppPixelName.BROKEN_SITE_REPORTED, mapOf("url" to url))
        verify(mockBrokenSiteSender).submitBrokenSiteFeedback(brokenSiteExpected)
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenCategoryIsNotLoginsThenDoNotUseLoginSite() {
        val categoryIndex = testee.shuffledCategories.indexOfFirst { it.key == BrokenSiteCategory.COMMENTS_CATEGORY_KEY }

        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )
        selectAndAcceptCategory(categoryIndex)
        testee.onSubmitPressed("webViewVersion", "description", "test")

        val brokenSiteExpected = BrokenSite(
            category = testee.shuffledCategories[categoryIndex].key,
            description = "description",
            siteUrl = url,
            upgradeHttps = false,
            blockedTrackers = "",
            surrogates = "",
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.MOBILE_SITE,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = "[]",
            httpErrorCodes = "",
            loginSite = "",
            reportFlow = ReportFlow.MENU,
        )

        verify(mockPixel).fire(AppPixelName.BROKEN_SITE_REPORTED, mapOf("url" to url))
        verify(mockBrokenSiteSender).submitBrokenSiteFeedback(brokenSiteExpected)
        verify(mockCommandObserver).onChanged(Command.ConfirmAndFinish)
    }

    @Test
    fun whenSiteProtectionsToggledAllowlistIsUpdated() = runTest {
        val url = "https://stuff.example.com"

        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )

        testee.onProtectionsToggled(protectionsEnabled = false)

        verify(mockUserAllowListRepository).addDomainToUserAllowList("stuff.example.com")
        verify(mockUserAllowListRepository, never()).removeDomainFromUserAllowList(anyString())

        clearInvocations(mockUserAllowListRepository)

        testee.onProtectionsToggled(protectionsEnabled = true)

        verify(mockUserAllowListRepository).removeDomainFromUserAllowList("stuff.example.com")
        verify(mockUserAllowListRepository, never()).addDomainToUserAllowList(anyString())
    }

    @Test
    fun whenContentBlockingIsDisabledThenSiteProtectionsAreDisabled() {
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.ContentBlockingFeatureName.value))
            .thenReturn(false)

        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )

        assertEquals(SiteProtectionsState.DISABLED_BY_REMOTE_CONFIG, viewState.protectionsState)
    }

    @Test
    fun whenUrlIsInUnprotectedTemporaryExceptionsThenSiteProtectionsAreDisabled() {
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.ContentBlockingFeatureName.value))
            .thenReturn(true)

        whenever(mockContentBlocking.isAnException(url)).thenReturn(false)
        whenever(mockUnprotectedTemporary.isAnException(url)).thenReturn(true)

        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )

        assertEquals(SiteProtectionsState.DISABLED_BY_REMOTE_CONFIG, viewState.protectionsState)
    }

    @Test
    fun whenUrlIsInContentBlockingExceptionsThenSiteProtectionsAreDisabled() {
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.ContentBlockingFeatureName.value))
            .thenReturn(true)

        whenever(mockContentBlocking.isAnException(url)).thenReturn(true)
        whenever(mockUnprotectedTemporary.isAnException(url)).thenReturn(false)

        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )

        assertEquals(SiteProtectionsState.DISABLED_BY_REMOTE_CONFIG, viewState.protectionsState)
    }

    @Test
    fun whenUrlIsInUserAllowlistThenSiteProtectionsAreDisabled() {
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.ContentBlockingFeatureName.value))
            .thenReturn(true)

        whenever(mockUserAllowListRepository.domainsInUserAllowListFlow()).thenReturn(flowOf(listOf("example.com")))

        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )

        assertEquals(SiteProtectionsState.DISABLED, viewState.protectionsState)
    }

    @Test
    fun whenUrlIsNotInUserAllowlistThenSiteProtectionsAreEnabled() {
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.ContentBlockingFeatureName.value))
            .thenReturn(true)

        whenever(mockUserAllowListRepository.domainsInUserAllowListFlow()).thenReturn(flowOf(emptyList()))

        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )

        assertEquals(SiteProtectionsState.ENABLED, viewState.protectionsState)
    }

    @Test
    fun whenUrlIsAddedToUserAllowlistThenPixelIsFired() = runTest {
        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )

        testee.onProtectionsToggled(protectionsEnabled = false)
        verify(mockPixel).fire(AppPixelName.BROKEN_SITE_ALLOWLIST_ADD)
    }

    @Test
    fun whenUrlIsRemovedFromUserAllowlistThenPixelIsFired() = runTest {
        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )

        testee.onProtectionsToggled(protectionsEnabled = true)
        verify(mockPixel).fire(AppPixelName.BROKEN_SITE_ALLOWLIST_REMOVE)
    }

    @Test
    fun whenProtectionsAreToggledThenPrivacyProtectionsPopupListenerIsInvoked() = runTest {
        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )

        testee.onProtectionsToggled(protectionsEnabled = false)
        verify(mockPrivacyProtectionsToggleUsageListener).onPrivacyProtectionsToggleUsed()
        testee.onProtectionsToggled(protectionsEnabled = true)
        verify(mockPrivacyProtectionsToggleUsageListener, times(2)).onPrivacyProtectionsToggleUsed()
    }

    @Test
    fun whenPrivacyProtectionsAreToggledThenCorrectPixelsAreSent() = runTest {
        val params = mapOf("test_key" to "test_value")
        privacyProtectionsPopupExperimentPixelParamsProvider.params = params
        testee.setInitialBrokenSite(
            url = url,
            blockedTrackers = "",
            surrogates = "",
            upgradedHttps = false,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = emptyArray(),
            httpErrorCodes = "",
            isDesktopMode = false,
            reportFlow = MENU,
        )

        testee.onProtectionsToggled(protectionsEnabled = false)
        verify(mockPixel).fire(AppPixelName.BROKEN_SITE_ALLOWLIST_ADD, params, type = COUNT)
        verify(mockPixel).fire(AppPixelName.BROKEN_SITE_ALLOWLIST_ADD_UNIQUE, params, type = UNIQUE)

        testee.onProtectionsToggled(protectionsEnabled = true)
        verify(mockPixel).fire(AppPixelName.BROKEN_SITE_ALLOWLIST_REMOVE, params, type = COUNT)
        verify(mockPixel).fire(AppPixelName.BROKEN_SITE_ALLOWLIST_REMOVE_UNIQUE, params, type = UNIQUE)
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

private class FakePrivacyProtectionsPopupExperimentPixelParamsProvider : PrivacyProtectionsPopupExperimentPixelParamsProvider {
    var params: Map<String, String> = emptyMap()

    override suspend fun getPixelParams(): Map<String, String> = params
}
