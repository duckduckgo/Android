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

package com.duckduckgo.app.privacymonitor.model

import com.duckduckgo.app.privacymonitor.PrivacyMonitor
import com.duckduckgo.app.privacymonitor.model.HttpsStatus.*
import com.duckduckgo.app.trackerdetection.model.TrackerNetwork
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyMonitorGradeExtensionTest {

    companion object {
        val defaultScore = 1
    }

    @Test
    fun whenHttpsMixedThenScoreIsIncrementedByOne() {
        val privacyMonitor = monitor(https = MIXED)
        assertEquals(defaultScore + 1, privacyMonitor.score)
    }

    @Test
    fun whenHttpThenScoreIsIncrementedByOne() {
        val privacyMonitor = monitor(https = NONE)
        assertEquals(defaultScore + 1, privacyMonitor.score)
    }

    @Test
    fun whenHttpsThenScoreIsUnchanged() {
        val privacyMonitor = monitor(https = SECURE)
        assertEquals(defaultScore, privacyMonitor.score)
    }

    @Test
    fun whenTermsClassificationIsAThenScoreIsDecrementedByOne() {
        val privacyMonitor = monitor(terms = TermsOfService(classification = "A"))
        assertEquals(defaultScore - 1, privacyMonitor.score)
    }

    @Test
    fun whenTermsClassificationIsBThenScoreIsUnchanged() {
        val privacyMonitor = monitor(terms = TermsOfService(classification = "B"))
        assertEquals(defaultScore, privacyMonitor.score)
    }

    @Test
    fun whenTermsClassificationIsCThenScoreIsUnchanged() {
        val privacyMonitor = monitor(terms = TermsOfService(classification = "C"))
        assertEquals(defaultScore, privacyMonitor.score)
    }

    @Test
    fun whenTermsClassificationIsDThenScoreIsIncrementedByOne() {
        val privacyMonitor = monitor(terms = TermsOfService(classification = "D"))
        assertEquals(defaultScore + 1, privacyMonitor.score)
    }

    @Test
    fun whenTermsClassificationIsEThenScoreIsIncrementedByTwo() {
        val privacyMonitor = monitor(terms = TermsOfService(classification = "E"))
        assertEquals(defaultScore + 2, privacyMonitor.score)
    }

    @Test
    fun whenTermsScoreIsPositiveThenScoreIsIncrementedByOne() {
        val privacyMonitor = monitor(terms = TermsOfService(score = 5))
        assertEquals(defaultScore + 1, privacyMonitor.score)
    }

    @Test
    fun whenTermsScoreIsNegativeThenScoreGreaterThanOneIsDecrementedByOne() {
        // http adds +1 and tos score removes 1 so we expect default score
        val privacyMonitor = monitor(terms = TermsOfService(score = -5), https = NONE)
        assertEquals(defaultScore, privacyMonitor.score)
    }

    @Test
    fun whenTermsScoreIsNegativeThenScoreOfOneIsUnchanged() {
        val privacyMonitor = monitor(terms = TermsOfService(score = -5))
        assertEquals(defaultScore, privacyMonitor.score)
    }

    @Test
    fun whenTermsScoreIsZeroThenScoreIsUnchanged() {
        val privacyMonitor = monitor(terms = TermsOfService(score = 0))
        assertEquals(defaultScore, privacyMonitor.score)
    }

    @Test
    fun whenZeroTrackersThenScoreIsDefault() {
        val privacyMonitor = monitor(trackerCount = 0)
        assertEquals(defaultScore, privacyMonitor.score)
    }

    @Test
    fun whenOneTrackerThenScoreNotIncremented() {
        val privacyMonitor = monitor(trackerCount = 1)
        assertEquals(defaultScore, privacyMonitor.score)
    }

    @Test
    fun whenTenTrackersThenScoreIsIncrementedByOne() {
        val privacyMonitor = monitor(trackerCount = 10)
        assertEquals(defaultScore + 1, privacyMonitor.score)
    }

    @Test
    fun whenTwentyTrackersThenScoreIsIncrementedByTwo() {
        val privacyMonitor = monitor(trackerCount = 20)
        assertEquals(defaultScore + 2, privacyMonitor.score)
    }

    @Test
    fun whenSiteIsMajorNetworkMemberThenScoreIsIncrementedByPercentage() {
        val privacyMonitor = monitor(memberNetwork = TrackerNetwork("", "", "", 45, true))
        assertEquals(defaultScore + 5, privacyMonitor.score)
    }

    @Test
    fun whenHasMajorTrackerThenScoreIsIncrementedByOne() {
        val privacyMonitor = monitor(hasTrackerFromMajorNetwork = true)
        assertEquals(defaultScore + 1, privacyMonitor.score)
    }

    @Test
    fun whenHasObscureTrackerThenScoreIsIncrementedByOne() {
        val privacyMonitor = monitor(hasObscureTracker = true)
        assertEquals(defaultScore + 1, privacyMonitor.score)
    }

    @Test
    fun whenPotentialScoreThenTrackerMetricsIgnored() {
        val privacyMonitor = monitor(
                TrackerNetwork("", "", "", 5, true),
                TermsOfService(classification = "D"),
                NONE,
                11,
                true,
                true)
        assertEquals(defaultScore + 6, privacyMonitor.score)
        assertEquals(defaultScore + 3, privacyMonitor.potentialScore)
    }

    @Test
    fun whenAllTrackersBlockedThenImprovedScoreIsEqualToPotentialScore() {
        val privacyMonitor = monitor(
                TrackerNetwork("", "", "", 5, true),
                TermsOfService(classification = "D"),
                NONE,
                5,
                true,
                true,
                allTrackerBlocked = true)
        assertEquals(privacyMonitor.potentialScore, privacyMonitor.improvedScore)
    }

    @Test
    fun whenNotAllTrackersBlockedThenImprovedScoreIsEqualToScore() {
        val privacyMonitor = monitor(
                TrackerNetwork("", "", "", 5, true),
                TermsOfService(classification = "D"),
                NONE,
                5,
                true,
                true,
                allTrackerBlocked = false)
        assertEquals(privacyMonitor.score, privacyMonitor.improvedScore)
    }

    private fun monitor(memberNetwork: TrackerNetwork? = null,
                        terms: TermsOfService = TermsOfService(),
                        https: HttpsStatus = SECURE,
                        trackerCount: Int = 0,
                        hasTrackerFromMajorNetwork: Boolean = false,
                        hasObscureTracker: Boolean = false,
                        allTrackerBlocked: Boolean = true): PrivacyMonitor {
        val monitor: PrivacyMonitor = mock()
        whenever(monitor.memberNetwork).thenReturn(memberNetwork)
        whenever(monitor.termsOfService).thenReturn(terms)
        whenever(monitor.trackerCount).thenReturn(trackerCount)
        whenever(monitor.hasTrackerFromMajorNetwork).thenReturn(hasTrackerFromMajorNetwork)
        whenever(monitor.hasObscureTracker).thenReturn(hasObscureTracker)
        whenever(monitor.https).thenReturn(https)
        whenever(monitor.allTrackersBlocked).thenReturn(allTrackerBlocked)
        return monitor
    }
}