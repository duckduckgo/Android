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

package com.duckduckgo.adclick.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.adclick.impl.pixels.AdClickPixelName
import com.duckduckgo.adclick.impl.pixels.AdClickPixels
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DuckDuckGoAdClickManagerTest {

    private val mockAdClickData: AdClickData = mock()
    private val mockAdClickAttribution: AdClickAttribution = mock()
    private val mockAdClickPixels: AdClickPixels = mock()
    private lateinit var testee: AdClickManager

    @Before
    fun before() {
        testee = DuckDuckGoAdClickManager(mockAdClickData, mockAdClickAttribution, mockAdClickPixels)
    }

    @Test
    fun whenDetectAdClickCalledForNullUrlThenReturn() {
        testee.detectAdClick(url = null, isMainFrame = false)

        verifyNoInteractions(mockAdClickData)
    }

    @Test
    fun whenDetectAdClickCalledForNonNullUrlAndNotMainFrameThenReturn() {
        testee.detectAdClick(url = "url", isMainFrame = false)

        verifyNoInteractions(mockAdClickData)
    }

    @Test
    fun whenDetectAdClickCalledForAdUrlAndIsMainFrameAndOnlyHeuristicDetectionEnabledThenUpdateAdDomainWithEmptyString() {
        whenever(mockAdClickAttribution.isAdClick(any())).thenReturn(Pair(true, null))
        whenever(mockAdClickAttribution.isDomainDetectionEnabled()).thenReturn(false)
        whenever(mockAdClickAttribution.isHeuristicDetectionEnabled()).thenReturn(true)
        val url = "ad_url.com"

        testee.detectAdClick(url = url, isMainFrame = true)

        verify(mockAdClickData).setAdDomainTldPlusOne("")
    }

    @Test
    fun whenDetectAdClickCalledForAdUrlAndIsMainFrameAndOnlyDomainDetectionEnabledThenUpdateAdDomainWithValue() {
        whenever(mockAdClickAttribution.isAdClick(any())).thenReturn(Pair(true, "domain.com"))
        whenever(mockAdClickAttribution.isDomainDetectionEnabled()).thenReturn(true)
        whenever(mockAdClickAttribution.isHeuristicDetectionEnabled()).thenReturn(false)
        val url = "https://ad_url?ad_domain=domain.com"

        testee.detectAdClick(url = url, isMainFrame = true)

        verify(mockAdClickData).setAdDomainTldPlusOne("domain.com")
    }

    @Test
    fun whenDetectAdClickCalledForAdUrlAndIsMainFrameAndBothDetectionsEnabledAndDomainPresentThenUpdateAdDomainWithValue() {
        whenever(mockAdClickAttribution.isAdClick(any())).thenReturn(Pair(true, "domain.com"))
        whenever(mockAdClickAttribution.isDomainDetectionEnabled()).thenReturn(true)
        whenever(mockAdClickAttribution.isHeuristicDetectionEnabled()).thenReturn(true)
        val url = "https://ad_url?ad_domain=domain.com"

        testee.detectAdClick(url = url, isMainFrame = true)

        verify(mockAdClickData).setAdDomainTldPlusOne("domain.com")
    }

    @Test
    fun whenDetectAdClickCalledForAdUrlAndIsMainFrameAndBothDetectionsEnabledAndNoDomainPresentThenUpdateAdDomainWithEmptyString() {
        whenever(mockAdClickAttribution.isAdClick(any())).thenReturn(Pair(true, null))
        whenever(mockAdClickAttribution.isDomainDetectionEnabled()).thenReturn(true)
        whenever(mockAdClickAttribution.isHeuristicDetectionEnabled()).thenReturn(true)
        val url = "https://ad_url.com?other_param=value"

        testee.detectAdClick(url = url, isMainFrame = true)

        verify(mockAdClickData).setAdDomainTldPlusOne("")
    }

    @Test
    fun whenDetectAdClickCalledForNonAdUrlAndIsMainFrameAndExemptionExpiredThenUpdateExemptionsMap() {
        whenever(mockAdClickAttribution.isAdClick(any())).thenReturn(Pair(false, null))
        whenever(mockAdClickData.getExemption()).thenReturn(expired("host.com"))
        val url = "non_ad_url.com"

        testee.detectAdClick(url = url, isMainFrame = true)

        verify(mockAdClickData, never()).setAdDomainTldPlusOne(any())
        verify(mockAdClickData).removeExemption()
    }

    @Test
    fun whenDetectAdClickCalledForNonAdUrlAndIsMainFrameAndExemptionNotExpiredThenUpdateExemptionsMap() {
        whenever(mockAdClickAttribution.isAdClick(any())).thenReturn(Pair(false, null))
        whenever(mockAdClickData.getExemption()).thenReturn(notExpired("host.com"))
        val url = "non_ad_url.com"

        testee.detectAdClick(url = url, isMainFrame = true)

        verify(mockAdClickData, never()).setAdDomainTldPlusOne(any())
        verify(mockAdClickData).addExemption(any())
    }

    @Test
    fun whenSetActiveTabIdCalledWithNoSourceTabIdThenSetActiveTab() {
        val tabId = "tab_id"

        testee.setActiveTabId(tabId = tabId, url = "url", sourceTabId = null, sourceTabUrl = null)

        verify(mockAdClickData).setActiveTab(tabId)
        verify(mockAdClickData, never()).addExemption(tabId = any(), exemption = any())
    }

    @Test
    fun whenSetActiveTabIdCalledWithSourceTabInfoThenSetActiveTabAndPropagateExemption() {
        val tabId = "tab_id"
        val url = "https://asos.com/"
        val sourceTabId = "source_tab_id"
        val sourceTabUrl = "source_url"
        whenever(mockAdClickData.getExemption(sourceTabId)).thenReturn(notExpired("asos.com"))

        testee.setActiveTabId(tabId = tabId, url = url, sourceTabId = sourceTabId, sourceTabUrl = sourceTabUrl)

        verify(mockAdClickData).setActiveTab(tabId)
        verify(mockAdClickData).addExemption(tabId = any(), exemption = any())
    }

    @Test
    fun whenSetActiveTabIdCalledWithSourceTabInfoAndExemptionExistsThenSetActiveTabAndDoNotPropagateExemptionFromSource() {
        val tabId = "tab_id"
        val url = "https://asos.com/"
        val sourceTabId = "source_tab_id"
        val sourceTabUrl = "source_url"
        whenever(mockAdClickData.getExemption(sourceTabId)).thenReturn(notExpired("asos.com"))
        whenever(mockAdClickData.getExemption()).thenReturn(notExpired("onbuy.com"))

        testee.setActiveTabId(tabId = tabId, url = url, sourceTabId = sourceTabId, sourceTabUrl = sourceTabUrl)

        verify(mockAdClickData).setActiveTab(tabId)
        verify(mockAdClickData, never()).addExemption(tabId = any(), exemption = any())
    }

    @Test
    fun whenDetectAdDomainCalledWithNoAdUrlThenRemoveAdDomain() {
        testee.detectAdDomain(url = "https://no_ad.com/")

        verify(mockAdClickData).removeAdDomain()
        verify(mockAdClickData, never()).addExemption(any())
    }

    @Test
    fun whenDetectAdDomainCalledWithDuckDuckGoUrlThenReturn() {
        testee.detectAdDomain(url = "https://duckduckgo.com")

        verify(mockAdClickData, never()).addExemption(any())
    }

    @Test
    fun whenDetectAdDomainCalledWithAdUrlThenAddExemptionAndRemoveAdDomain() {
        whenever(mockAdClickData.getAdDomainTldPlusOne()).thenReturn("landing_page.com")

        testee.detectAdDomain(url = "https://landing_page.com/")

        verify(mockAdClickData).addExemption(any())
        verify(mockAdClickData).removeAdDomain()
    }

    @Test
    fun whenDetectAdDomainCalledWithAdUrlAndSerpDomainMatchesUrlDomainThenDomainDetectionPixelSentWithMatched() {
        whenever(mockAdClickData.getAdDomainTldPlusOne()).thenReturn("landing_page.com")

        testee.detectAdDomain(url = "https://landing_page.com/")

        verify(mockAdClickPixels).fireAdClickDetectedPixel(
            savedAdDomain = "landing_page.com",
            urlAdDomain = "landing_page.com",
            heuristicEnabled = false,
            domainEnabled = false,
        )
    }

    @Test
    fun whenDetectAdDomainCalledWithAdUrlAndSerpDomainNotMatchesUrlDomainThenDomainDetectionPixelSentWithMismatch() {
        whenever(mockAdClickData.getAdDomainTldPlusOne()).thenReturn("landing_page.com")

        testee.detectAdDomain(url = "https://other_landing_page.com/")

        verify(mockAdClickPixels).fireAdClickDetectedPixel(
            savedAdDomain = "landing_page.com",
            urlAdDomain = "other_landing_page.com",
            heuristicEnabled = false,
            domainEnabled = false,
        )
    }

    @Test
    fun whenDetectAdDomainCalledWithAdUrlAndSerpDomainAndBrokenUrlDomainThenDomainDetectionPixelSentWithSerpOnly() {
        whenever(mockAdClickData.getAdDomainTldPlusOne()).thenReturn("landing_page")

        testee.detectAdDomain(url = "https://")

        verify(mockAdClickPixels).fireAdClickDetectedPixel(
            savedAdDomain = "landing_page",
            urlAdDomain = "",
            heuristicEnabled = false,
            domainEnabled = false,
        )
    }

    @Test
    fun whenDetectAdDomainCalledWithAdUrlAndNoSerpDomainAndUrlDomainThenDomainDetectionPixelSentWithHeuristicOnly() {
        whenever(mockAdClickData.getAdDomainTldPlusOne()).thenReturn("")

        testee.detectAdDomain(url = "https://landing_page.com/")

        verify(mockAdClickPixels).fireAdClickDetectedPixel(
            savedAdDomain = "",
            urlAdDomain = "landing_page.com",
            heuristicEnabled = false,
            domainEnabled = false,
        )
    }

    @Test
    fun whenDetectAdDomainCalledWithAdUrlAndNoSerpDomainAndEmptyUrlDomainThenDomainDetectionPixelSentWithNone() {
        whenever(mockAdClickData.getAdDomainTldPlusOne()).thenReturn("")

        testee.detectAdDomain(url = "https://")

        verify(mockAdClickPixels).fireAdClickDetectedPixel(
            savedAdDomain = "",
            urlAdDomain = "",
            heuristicEnabled = false,
            domainEnabled = false,
        )
    }

    @Test
    fun whenDetectAdDomainCalledAndHeuristicsEnabledThenDomainDetectionPixelSentWithHeuristicsToTrue() {
        whenever(mockAdClickData.getAdDomainTldPlusOne()).thenReturn("")
        whenever(mockAdClickAttribution.isHeuristicDetectionEnabled()).thenReturn(true)

        testee.detectAdDomain(url = "https://")

        verify(mockAdClickPixels).fireAdClickDetectedPixel(
            savedAdDomain = "",
            urlAdDomain = "",
            heuristicEnabled = true,
            domainEnabled = false,
        )
    }

    @Test
    fun whenDetectAdDomainCalledAndDomainDetectionEnabledThenDomainDetectionPixelSentWithDomainDetectionToTrue() {
        whenever(mockAdClickData.getAdDomainTldPlusOne()).thenReturn("")
        whenever(mockAdClickAttribution.isDomainDetectionEnabled()).thenReturn(true)

        testee.detectAdDomain(url = "https://")

        verify(mockAdClickPixels).fireAdClickDetectedPixel(
            savedAdDomain = "",
            urlAdDomain = "",
            heuristicEnabled = false,
            domainEnabled = true,
        )
    }

    @Test
    fun whenClearTabIdCalledForTabThenRemoveDataForTab() {
        val tabId = "tab_id"

        testee.clearTabId(tabId)

        verify(mockAdClickData).remove(tabId)
    }

    @Test
    fun whenClearAllCalledThenRemoveAllData() {
        testee.clearAll()

        verify(mockAdClickData).removeAll()
    }

    @Test
    fun whenClearAllExpiredCalledThenRemoveAllExpiredData() {
        testee.clearAllExpiredAsync()

        verify(mockAdClickData).removeAllExpired()
    }

    @Test
    fun whenIsExemptionCalledWithDuckDuckGoDocumentUrlThenReturnFalse() {
        val documentUrl = "https://duckduckgo.com"
        val url = "https://tracker.com"

        val result = testee.isExemption(documentUrl = documentUrl, url = url)
        verify(mockAdClickPixels, never()).fireAdClickActivePixel(any())
        verify(mockAdClickPixels, never()).updateCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)
        assertFalse(result)
    }

    @Test
    fun whenIsExemptionCalledWithUrlMatchingExpiredExemptionThenReturnFalse() {
        val documentUrl = "https://asos.com"
        val documentUrlHost = "asos.com"
        val url = "https://tracker.com"

        whenever(mockAdClickData.isHostExempted(documentUrlHost)).thenReturn(true)
        whenever(mockAdClickData.getExemption()).thenReturn(expired(documentUrlHost))

        val result = testee.isExemption(documentUrl = documentUrl, url = url)

        verify(mockAdClickData).removeExemption()
        verify(mockAdClickPixels, never()).fireAdClickActivePixel(any())
        verify(mockAdClickPixels, never()).updateCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)
        assertFalse(result)
    }

    @Test
    fun whenIsExemptionCalledWithUrlNotMatchingExpiredExemptionNotMatchingTrackerThenReturnFalse() {
        val documentUrl = "https://asos.com"
        val documentUrlHost = "asos.com"
        val url = "https://tracker.com"

        whenever(mockAdClickData.isHostExempted(documentUrlHost)).thenReturn(true)
        whenever(mockAdClickData.getExemption()).thenReturn(notExpired(documentUrlHost))
        whenever(mockAdClickAttribution.isAllowed(url)).thenReturn(false)

        val result = testee.isExemption(documentUrl = documentUrl, url = url)

        verify(mockAdClickData, never()).removeExemption()
        verify(mockAdClickPixels, never()).fireAdClickActivePixel(any())
        verify(mockAdClickPixels, never()).updateCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)
        assertFalse(result)
    }

    @Test
    fun whenIsExemptionCalledWithUrlNotMatchingExpiredExemptionAndMatchingTrackerThenSendPixelAndReturnTrue() {
        val documentUrl = "https://asos.com"
        val documentUrlHost = "asos.com"
        val url = "https://bat.bing.com"

        whenever(mockAdClickData.isHostExempted(documentUrlHost)).thenReturn(true)
        whenever(mockAdClickData.getExemption()).thenReturn(notExpired(documentUrlHost))
        whenever(mockAdClickData.getCurrentPage()).thenReturn(documentUrl)
        whenever(mockAdClickAttribution.isAllowed(url)).thenReturn(true)
        testee.detectAdDomain(url = "https://asos.com/")

        val result = testee.isExemption(documentUrl = documentUrl, url = url)

        verify(mockAdClickData, never()).removeExemption()
        verify(mockAdClickPixels).fireAdClickActivePixel(any())
        verify(mockAdClickPixels, times(1)).updateCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)
        assertTrue(result)
    }

    @Test
    fun whenIsExemptionCalledWithETldPlusOneExemptedAndMatchingTrackerThenSendPixelAndReturnTrue() {
        val documentUrl = "https://uk.asos.com"
        val documentUrlHost = "uk.asos.com" // notice the host: uk.asos.com
        val documentUrlTlDPlusOne = "asos.com" // notice the eTLD+1: asos.com
        val url = "https://bat.bing.com"

        whenever(mockAdClickData.isHostExempted(documentUrlHost)).thenReturn(false)
        whenever(mockAdClickData.isHostExempted(documentUrlTlDPlusOne)).thenReturn(true)
        whenever(mockAdClickData.getExemption()).thenReturn(notExpired(documentUrlTlDPlusOne))
        whenever(mockAdClickData.getCurrentPage()).thenReturn(documentUrl)
        whenever(mockAdClickAttribution.isAllowed(url)).thenReturn(true)

        val result = testee.isExemption(documentUrl = documentUrl, url = url)

        verify(mockAdClickData, never()).removeExemption()
        verify(mockAdClickPixels).fireAdClickActivePixel(any())
        assertTrue(result)
    }

    private fun expired(hostTldPlusOne: String) = Exemption(
        hostTldPlusOne = hostTldPlusOne,
        navigationExemptionDeadline = 0L,
        exemptionDeadline = 0L,
    )

    private fun notExpired(hostTldPlusOne: String) = Exemption(
        hostTldPlusOne = hostTldPlusOne,
        navigationExemptionDeadline = Exemption.NO_EXPIRY,
        exemptionDeadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10),
    )
}
