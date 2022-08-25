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

import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.adclick.impl.pixels.AdClickPixelName
import com.duckduckgo.adclick.impl.pixels.AdClickPixelName.AD_CLICK_DETECTED
import com.duckduckgo.adclick.impl.pixels.AdClickPixelParameters.AD_CLICK_DOMAIN_DETECTION
import com.duckduckgo.adclick.impl.pixels.AdClickPixelParameters.AD_CLICK_DOMAIN_DETECTION_ENABLED
import com.duckduckgo.adclick.impl.pixels.AdClickPixelParameters.AD_CLICK_HEURISTIC_DETECTION
import com.duckduckgo.adclick.impl.pixels.AdClickPixelValues.AD_CLICK_DETECTED_HEURISTIC_ONLY
import com.duckduckgo.adclick.impl.pixels.AdClickPixelValues.AD_CLICK_DETECTED_MATCHED
import com.duckduckgo.adclick.impl.pixels.AdClickPixelValues.AD_CLICK_DETECTED_MISMATCH
import com.duckduckgo.adclick.impl.pixels.AdClickPixelValues.AD_CLICK_DETECTED_NONE
import com.duckduckgo.adclick.impl.pixels.AdClickPixelValues.AD_CLICK_DETECTED_SERP_ONLY
import com.duckduckgo.adclick.impl.pixels.AdClickPixels
import com.duckduckgo.app.statistics.pixels.Pixel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DuckDuckGoAdClickManagerTest {

    private val mockAdClickData: AdClickData = mock()
    private val mockAdClickAttribution: AdClickAttribution = mock()
    private val mockPixel: Pixel = mock()
    private val mockAdClickPixels: AdClickPixels = mock()
    private lateinit var testee: AdClickManager

    @Before
    fun before() {
        testee = DuckDuckGoAdClickManager(mockAdClickData, mockAdClickAttribution, mockPixel, mockAdClickPixels)
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
        verify(mockAdClickPixels).updateCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)
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
        verify(mockAdClickPixels, never()).updateCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)
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

        verify(mockPixel).fire(
            AD_CLICK_DETECTED,
            mapOf(
                AD_CLICK_DOMAIN_DETECTION to AD_CLICK_DETECTED_MATCHED,
                AD_CLICK_HEURISTIC_DETECTION to "false",
                AD_CLICK_DOMAIN_DETECTION_ENABLED to "false"
            )
        )
    }

    @Test
    fun whenDetectAdDomainCalledWithAdUrlAndSerpDomainNotMatchesUrlDomainThenDomainDetectionPixelSentWithMismatch() {
        whenever(mockAdClickData.getAdDomainTldPlusOne()).thenReturn("landing_page.com")

        testee.detectAdDomain(url = "https://other_landing_page.com/")

        verify(mockPixel).fire(
            AD_CLICK_DETECTED,
            mapOf(
                AD_CLICK_DOMAIN_DETECTION to AD_CLICK_DETECTED_MISMATCH,
                AD_CLICK_HEURISTIC_DETECTION to "false",
                AD_CLICK_DOMAIN_DETECTION_ENABLED to "false"
            )
        )
    }

    @Test
    fun whenDetectAdDomainCalledWithAdUrlAndSerpDomainAndBrokenUrlDomainThenDomainDetectionPixelSentWithSerpOnly() {
        whenever(mockAdClickData.getAdDomainTldPlusOne()).thenReturn("landing_page")

        testee.detectAdDomain(url = "https://")

        verify(mockPixel).fire(
            AD_CLICK_DETECTED,
            mapOf(
                AD_CLICK_DOMAIN_DETECTION to AD_CLICK_DETECTED_SERP_ONLY,
                AD_CLICK_HEURISTIC_DETECTION to "false",
                AD_CLICK_DOMAIN_DETECTION_ENABLED to "false"
            )
        )
    }

    @Test
    fun whenDetectAdDomainCalledWithAdUrlAndNoSerpDomainAndUrlDomainThenDomainDetectionPixelSentWithHeuristicOnly() {
        whenever(mockAdClickData.getAdDomainTldPlusOne()).thenReturn("")

        testee.detectAdDomain(url = "https://landing_page.com/")

        verify(mockPixel).fire(
            AD_CLICK_DETECTED,
            mapOf(
                AD_CLICK_DOMAIN_DETECTION to AD_CLICK_DETECTED_HEURISTIC_ONLY,
                AD_CLICK_HEURISTIC_DETECTION to "false",
                AD_CLICK_DOMAIN_DETECTION_ENABLED to "false"
            )
        )
    }

    @Test
    fun whenDetectAdDomainCalledWithAdUrlAndNoSerpDomainAndEmptyUrlDomainThenDomainDetectionPixelSentWithNone() {
        whenever(mockAdClickData.getAdDomainTldPlusOne()).thenReturn("")

        testee.detectAdDomain(url = "https://")

        verify(mockPixel).fire(
            AD_CLICK_DETECTED,
            mapOf(
                AD_CLICK_DOMAIN_DETECTION to AD_CLICK_DETECTED_NONE,
                AD_CLICK_HEURISTIC_DETECTION to "false",
                AD_CLICK_DOMAIN_DETECTION_ENABLED to "false"
            )
        )
    }

    @Test
    fun whenDetectAdDomainCalledAndHeuristicsEnabledThenDomainDetectionPixelSentWithHeuristicsToTrue() {
        whenever(mockAdClickData.getAdDomainTldPlusOne()).thenReturn("")
        whenever(mockAdClickAttribution.isHeuristicDetectionEnabled()).thenReturn(true)

        testee.detectAdDomain(url = "https://")

        verify(mockPixel).fire(
            AD_CLICK_DETECTED,
            mapOf(
                AD_CLICK_DOMAIN_DETECTION to AD_CLICK_DETECTED_NONE,
                AD_CLICK_HEURISTIC_DETECTION to "true",
                AD_CLICK_DOMAIN_DETECTION_ENABLED to "false"
            )
        )
    }

    @Test
    fun whenDetectAdDomainCalledAndDomainDetectionEnabledThenDomainDetectionPixelSentWithDomainDetectionToTrue() {
        whenever(mockAdClickData.getAdDomainTldPlusOne()).thenReturn("")
        whenever(mockAdClickAttribution.isDomainDetectionEnabled()).thenReturn(true)

        testee.detectAdDomain(url = "https://")

        verify(mockPixel).fire(
            AD_CLICK_DETECTED,
            mapOf(
                AD_CLICK_DOMAIN_DETECTION to AD_CLICK_DETECTED_NONE,
                AD_CLICK_HEURISTIC_DETECTION to "false",
                AD_CLICK_DOMAIN_DETECTION_ENABLED to "true"
            )
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
        verify(mockPixel, never()).fire(AdClickPixelName.AD_CLICK_ACTIVE)
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
        verify(mockPixel, never()).fire(AdClickPixelName.AD_CLICK_ACTIVE)
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
        verify(mockPixel, never()).fire(AdClickPixelName.AD_CLICK_ACTIVE)
        assertFalse(result)
    }

    @Test
    fun whenIsExemptionCalledWithUrlNotMatchingExpiredExemptionAndMatchingTrackerThenSendPixelAndReturnTrue() {
        val documentUrl = "https://asos.com"
        val documentUrlHost = "asos.com"
        val url = "https://bat.bing.com"

        whenever(mockAdClickData.isHostExempted(documentUrlHost)).thenReturn(true)
        whenever(mockAdClickData.getExemption()).thenReturn(notExpired(documentUrlHost))
        whenever(mockAdClickAttribution.isAllowed(url)).thenReturn(true)

        val result = testee.isExemption(documentUrl = documentUrl, url = url)

        verify(mockAdClickData, never()).removeExemption()
        verify(mockPixel).fire(AdClickPixelName.AD_CLICK_ACTIVE)
        assertTrue(result)
    }

    private fun expired(hostTldPlusOne: String) = Exemption(
        hostTldPlusOne = hostTldPlusOne,
        navigationExemptionDeadline = 0L,
        exemptionDeadline = 0L
    )

    private fun notExpired(hostTldPlusOne: String) = Exemption(
        hostTldPlusOne = hostTldPlusOne,
        navigationExemptionDeadline = Exemption.NO_EXPIRY,
        exemptionDeadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10)
    )
}
