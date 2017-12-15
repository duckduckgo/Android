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

package com.duckduckgo.app.privacymonitor

import com.duckduckgo.app.privacymonitor.HttpsStatus.NONE
import com.duckduckgo.app.privacymonitor.HttpsStatus.SECURE
import com.duckduckgo.app.privacymonitor.model.TermsOfService
import com.duckduckgo.app.privacymonitor.ui.score
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
    fun whenZeroTrackersThenScoreIsDefault() {
        val privacyMonitor = monitor(0)
        assertEquals(defaultScore, privacyMonitor.score)
    }

    @Test
    fun whenOneTrackerThenScoreIsIncrementedByOne() {
        val privacyMonitor = monitor(1)
        assertEquals(defaultScore + 1, privacyMonitor.score)
    }

    @Test
    fun whenElevenTrackersThenScoreIsIncrementedByTwo() {
        val privacyMonitor = monitor(11)
        assertEquals(defaultScore + 2, privacyMonitor.score)
    }

    @Test
    fun whenSiteIsMajorNetworkMemberThenScoreIsIncrementedByPercentage() {
        val privacyMonitor = monitor(memberNetwork = TrackerNetwork("", "", 45, true))
        assertEquals(defaultScore + 5, privacyMonitor.score)
    }

    @Test
    fun whenHasMajorTrackerThenScoreIsIncrementedByOne() {
        val privacyMonitor = monitor(majorTrackerCount = 1)
        assertEquals(defaultScore + 1, privacyMonitor.score)
    }

    @Test
    fun whenHasObscureTrackerThenScoreIsIncrementedByOne() {
        val privacyMonitor = monitor(hasObscureTracker = true)
        assertEquals(defaultScore + 1, privacyMonitor.score)
    }

    @Test
    fun whenHttpsSecureThenScoreIsDecrementedByOne() {
        val privacyMonitor = monitor(https = SECURE)
        assertEquals(defaultScore - 1, privacyMonitor.score)
    }

    //TODO terms tests
    //TODO improvedScore tests

    private fun monitor(trackerCount: Int = 0,
                        majorTrackerCount: Int = 0,
                        memberNetwork: TrackerNetwork? = null,
                        hasObscureTracker: Boolean = false,
                        https: HttpsStatus = NONE): PrivacyMonitor {

        val monitor: PrivacyMonitor = mock()
        whenever(monitor.termsOfService).thenReturn(TermsOfService())
        whenever(monitor.memberNetwork).thenReturn(memberNetwork)
        whenever(monitor.trackerCount).thenReturn(trackerCount)
        whenever(monitor.majorNetworkCount).thenReturn(majorTrackerCount)
        whenever(monitor.hasObscureTracker).thenReturn(hasObscureTracker)
        whenever(monitor.https).thenReturn(https)
        return monitor
    }
}