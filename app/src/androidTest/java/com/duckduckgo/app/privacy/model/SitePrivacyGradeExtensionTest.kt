/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.privacy.model

import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.model.HttpsStatus.*
import com.duckduckgo.app.trackerdetection.model.TrackerNetwork
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Test

class SitePrivacyGradeExtensionTest {

    @Test
    fun whenHttpsMixedThenScoreIsIncrementedByOne() {
        val site = site(https = MIXED)
        assertEquals(defaultScore + 1, site.score)
    }

    @Test
    fun whenHttpThenScoreIsIncrementedByOne() {
        val site = site(https = NONE)
        assertEquals(defaultScore + 1, site.score)
    }

    @Test
    fun whenHttpsThenScoreIsUnchanged() {
        val site = site(https = SECURE)
        assertEquals(defaultScore, site.score)
    }

    @Test
    fun whenTermsClassificationIsAThenScoreIsDecrementedByOne() {
        val site = site(terms = TermsOfService(classification = "A"))
        assertEquals(defaultScore - 1, site.score)
    }

    @Test
    fun whenTermsClassificationIsBThenScoreIsUnchanged() {
        val site = site(terms = TermsOfService(classification = "B"))
        assertEquals(defaultScore, site.score)
    }

    @Test
    fun whenTermsClassificationIsCThenScoreIsUnchanged() {
        val site = site(terms = TermsOfService(classification = "C"))
        assertEquals(defaultScore, site.score)
    }

    @Test
    fun whenTermsClassificationIsDThenScoreIsIncrementedByOne() {
        val site = site(terms = TermsOfService(classification = "D"))
        assertEquals(defaultScore + 1, site.score)
    }

    @Test
    fun whenTermsClassificationIsEThenScoreIsIncrementedByTwo() {
        val site = site(terms = TermsOfService(classification = "E"))
        assertEquals(defaultScore + 2, site.score)
    }

    @Test
    fun whenTermsScoreIsPositiveThenScoreIsIncrementedByOne() {
        val site = site(terms = TermsOfService(score = 5))
        assertEquals(defaultScore + 1, site.score)
    }

    @Test
    fun whenTermsScoreIsNegativeThenScoreGreaterThanOneIsDecrementedByOne() {
        // http adds +1 and tos score removes 1 so we expect default score
        val site = site(terms = TermsOfService(score = -5), https = NONE)
        assertEquals(defaultScore, site.score)
    }

    @Test
    fun whenTermsScoreIsNegativeThenScoreOfOneIsUnchanged() {
        val site = site(terms = TermsOfService(score = -5))
        assertEquals(defaultScore, site.score)
    }

    @Test
    fun whenTermsScoreIsZeroThenScoreIsUnchanged() {
        val site = site(terms = TermsOfService(score = 0))
        assertEquals(defaultScore, site.score)
    }

    @Test
    fun whenZeroTrackersThenScoreIsDefault() {
        val site = site(trackerCount = 0)
        assertEquals(defaultScore, site.score)
    }

    @Test
    fun whenOneTrackerThenScoreNotIncremented() {
        val site = site(trackerCount = 1)
        assertEquals(defaultScore, site.score)
    }

    @Test
    fun whenTenTrackersThenScoreIsIncrementedByOne() {
        val site = site(trackerCount = 10)
        assertEquals(defaultScore + 1, site.score)
    }

    @Test
    fun whenTwentyTrackersThenScoreIsIncrementedByTwo() {
        val site = site(trackerCount = 20)
        assertEquals(defaultScore + 2, site.score)
    }

    @Test
    fun whenSiteIsMajorNetworkMemberThenScoreIsIncrementedByPercentage() {
        val site = site(memberNetwork = TrackerNetwork("", "", "", 45, true))
        assertEquals(defaultScore + 5, site.score)
    }

    @Test
    fun whenHasMajorTrackerThenScoreIsIncrementedByOne() {
        val site = site(hasTrackerFromMajorNetwork = true)
        assertEquals(defaultScore + 1, site.score)
    }

    @Test
    fun whenHasObscureTrackerThenScoreIsIncrementedByOne() {
        val site = site(hasObscureTracker = true)
        assertEquals(defaultScore + 1, site.score)
    }

    @Test
    fun whenPotentialScoreThenTrackerMetricsIgnored() {
        val site = site(
                TrackerNetwork("", "", "", 5, true),
                TermsOfService(classification = "D"),
                NONE,
                11,
                true,
                true)
        assertEquals(defaultScore + 6, site.score)
        assertEquals(defaultScore + 3, site.potentialScore)
    }

    @Test
    fun whenAllTrackersBlockedThenImprovedScoreIsEqualToPotentialScore() {
        val site = site(
                TrackerNetwork("", "", "", 5, true),
                TermsOfService(classification = "D"),
                NONE,
                5,
                true,
                true,
                allTrackerBlocked = true)
        assertEquals(site.potentialScore, site.improvedScore)
    }

    @Test
    fun whenNotAllTrackersBlockedThenImprovedScoreIsEqualToScore() {
        val site = site(
                TrackerNetwork("", "", "", 5, true),
                TermsOfService(classification = "D"),
                NONE,
                5,
                true,
                true,
                allTrackerBlocked = false)
        assertEquals(site.score, site.improvedScore)
    }

    private fun site(memberNetwork: TrackerNetwork? = null,
                        terms: TermsOfService = TermsOfService(),
                        https: HttpsStatus = SECURE,
                        trackerCount: Int = 0,
                        hasTrackerFromMajorNetwork: Boolean = false,
                        hasObscureTracker: Boolean = false,
                        allTrackerBlocked: Boolean = true): Site {
        val site: Site = mock()
        whenever(site.memberNetwork).thenReturn(memberNetwork)
        whenever(site.termsOfService).thenReturn(terms)
        whenever(site.trackerCount).thenReturn(trackerCount)
        whenever(site.hasTrackerFromMajorNetwork).thenReturn(hasTrackerFromMajorNetwork)
        whenever(site.hasObscureTracker).thenReturn(hasObscureTracker)
        whenever(site.https).thenReturn(https)
        whenever(site.allTrackersBlocked).thenReturn(allTrackerBlocked)
        return site
    }

    companion object {
        private const val defaultScore = 1
    }
}