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

package com.duckduckgo.app.privacy.renderer

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacy.db.NetworkLeaderboardEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackersRendererTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testee = TrackersRenderer()

    @Test
    fun whenTrackersBlockedThenTrackersTextShowsRequestsBlockedFromLoading() {
        val text = testee.trackersText(context, trackersBlockedCount = 1, specialDomainsLoadedCount = 0)
        assertEquals("Requests Blocked from Loading", text)
    }

    @Test
    fun whenTrackersNoBlockedAndOneSpecialDomainLoadedThenTrackerTextShowsNoRequestsBlocked() {
        val text = testee.trackersText(context, trackersBlockedCount = 0, specialDomainsLoadedCount = 1)
        assertEquals("No Tracking Requests Blocked", text)
    }

    @Test
    fun whenTrackersNoBlockedAndNoDomainsLoadedThenTrackerTextShowsNoRequestsFound() {
        val text = testee.trackersText(context, trackersBlockedCount = 0, specialDomainsLoadedCount = 0)
        assertEquals("No Tracking Requests Found", text)
    }

    @Test
    fun whenDomainsLoadedThenDomainsLoadedTextShowThirdPartyRequestsLoaded() {
        val text = testee.domainsLoadedText(context, domainsLoadedCount = 1)
        assertEquals("Third-Party Requests Loaded", text)
    }

    @Test
    fun whenNoDomainsLoadedThenDomainsLoadedTextShowsNoThirdPartyRequestsLoaded() {
        val text = testee.domainsLoadedText(context, domainsLoadedCount = 0)
        assertEquals("No Third-Party Requests Loaded", text)
    }

    @Test
    fun whenNoMajorNetworksThenNetworksTextShowsNoFound() {
        val text = testee.majorNetworksText(context, 0)
        assertEquals("No Major Tracker Networks Found", text)
    }

    @Test
    fun whenTenMajorNetworksAndNotAllBlockedThenNetworksTextShowsFound() {
        val text = testee.majorNetworksText(context, 1)
        assertEquals("Major Tracker Networks Found", text)
    }

    @Test
    fun whenSitesVisitedIsZeroThenPercentageIsBlank() {
        val text = testee.networkPercentage(NetworkLeaderboardEntry("", 10), 0)
        assertEquals("", text)
    }

    @Test
    fun whenNetworkCountIsZeroThenPercentageIsBlank() {
        val text = testee.networkPercentage(NetworkLeaderboardEntry("", 0), 10)
        assertEquals("", text)
    }

    @Test
    fun whenPortionIsRecurringFractionThenPercentageIsRoundNumber() {
        val text = testee.networkPercentage(NetworkLeaderboardEntry("", 10), 30)
        assertEquals("33%", text)
    }

    @Test
    fun whenPortionIsHalfThenPercentageIs50Percent() {
        val text = testee.networkPercentage(NetworkLeaderboardEntry("", 10), 20)
        assertEquals("50%", text)
    }

    @Test
    fun whenToggleDisabledAndTrackersCountMoreThanZeroThenNetworksIconIsBad() {
        val resource = testee.networksIcon(
            trackersBlockedCount = 1,
            specialDomainsLoadedCount = 0,
            toggleEnabled = false,
        )
        assertEquals(R.drawable.networks_icon_bad, resource)
    }

    @Test
    fun whenToggleDisabledAndSpecialDomainsCountMoreThanZeroThenNetworksIconIsBad() {
        val resource = testee.networksIcon(
            trackersBlockedCount = 0,
            specialDomainsLoadedCount = 1,
            toggleEnabled = false,
        )
        assertEquals(R.drawable.networks_icon_bad, resource)
    }

    @Test
    fun whenToggleDisabledAndTrackersAndSpecialDomainsCountIsZeroThenNetworksIconIsGood() {
        val resource = testee.networksIcon(
            trackersBlockedCount = 0,
            specialDomainsLoadedCount = 0,
            toggleEnabled = false,
        )
        assertEquals(R.drawable.networks_icon_good, resource)
    }

    @Test
    fun whenToggleEnabledAndTrackersCountZeroAndDomainsCountMoreThanZeroThenNetworksIconIsNeutral() {
        val resource = testee.networksIcon(
            trackersBlockedCount = 0,
            specialDomainsLoadedCount = 1,
            toggleEnabled = true,
        )
        assertEquals(R.drawable.networks_icon_neutral, resource)
    }

    @Test
    fun whenToggleEnabledAndTrackersCountZeroAndDomainsCountIsZeroThenNetworksIconIsGood() {
        val resource = testee.networksIcon(
            trackersBlockedCount = 0,
            specialDomainsLoadedCount = 0,
            toggleEnabled = true,
        )
        assertEquals(R.drawable.networks_icon_good, resource)
    }

    @Test
    fun whenToggleEnabledAndTrackersCountMoreThanZeroAndDomainsCountIsZeroThenNetworksIconIsGood() {
        val resource = testee.networksIcon(
            trackersBlockedCount = 1,
            specialDomainsLoadedCount = 0,
            toggleEnabled = true,
        )
        assertEquals(R.drawable.networks_icon_good, resource)
    }

    @Test
    fun whenToggleEnabledAndTrackersCountMoreThanZeroAndDomainsCountMoreThanZeroThenNetworksIconIsGood() {
        val resource = testee.networksIcon(
            trackersBlockedCount = 1,
            specialDomainsLoadedCount = 1,
            toggleEnabled = true,
        )
        assertEquals(R.drawable.networks_icon_good, resource)
    }

    @Test
    fun whenToggleEnabledAndTrackersCountMoreThanZeroAndDomainsCountMoreThanZeroAndLargeIconNeededThenNetworksIconIsGoodAndLarge() {
        val resource = testee.networksIcon(
            trackersBlockedCount = 1,
            specialDomainsLoadedCount = 1,
            toggleEnabled = true,
            largeIcon = true,
        )
        assertEquals(R.drawable.networks_icon_good_large, resource)
    }

    @Test
    fun whenNetworkNameMatchesPillIconThenResourceIsReturned() {
        val resource = testee.networkPillIcon(context, "outbrain")
        assertEquals(R.drawable.network_pill_outbrain, resource)
    }

    @Test
    fun whenNetworkNameMatchesLogoIconThenResourceIsReturned() {
        val resource = testee.networkLogoIcon(context, "outbrain")
        assertEquals(R.drawable.network_logo_outbrain, resource)
    }

    @Test
    fun whenNetworkNameSansSpecialCharactersAndWithUnderscoresForSpacesMatchesLogoIconThenResourceIsReturned() {
        val resource = testee.networkLogoIcon(context, "Amazon Technologies, Inc.")
        assertEquals(R.drawable.network_logo_amazon_technologies_inc, resource)
    }

    @Test
    fun whenNetworkNameSansSpecialCharactersAndWithUnderscoresForSpacesMatchesPillIconWithUnderscoresThenResourceIsReturned() {
        val resource = testee.networkPillIcon(context, "Amazon Technologies, Inc.")
        assertEquals(R.drawable.network_pill_amazon_technologies_inc, resource)
    }

    @Test
    fun whenNetworkNameDoesNotMatchLogoIconThenNull() {
        val resource = testee.networkLogoIcon(context, "unknownnetwork")
        assertNull(resource)
    }

    @Test
    fun whenCountIsZeroThenIconIsSuccess() {
        assertEquals(R.drawable.icon_success, testee.successFailureIcon(0))
    }

    @Test
    fun whenCountIsNotZeroThenIconIsFailure() {
        assertEquals(R.drawable.icon_fail, testee.successFailureIcon(1))
    }
}
