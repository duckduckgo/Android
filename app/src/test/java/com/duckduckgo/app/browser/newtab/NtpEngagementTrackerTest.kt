/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.browser.newtab

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NtpEngagementTrackerTest {

    private lateinit var testee: NtpEngagementTracker

    @Before
    fun setup() {
        testee = NtpEngagementTracker()
    }

    @Test
    fun whenEngagementHasNotBeenReportedThenShouldReportEngagement() {
        assertTrue(testee.shouldReportEngagement())
    }

    @Test
    fun whenEngagementHasAlreadyBeenReportedThenShouldNotReportEngagementAgain() {
        testee.shouldReportEngagement()

        assertFalse(testee.shouldReportEngagement())
    }

    @Test
    fun whenFreshLaunchOpensThenEngagementCanBeReportedAgain() {
        testee.shouldReportEngagement()

        testee.onOpen(isFreshLaunch = true)

        assertTrue(testee.shouldReportEngagement())
    }

    @Test
    fun whenNonFreshLaunchOpensThenEngagementCanBeReportedAgain() {
        testee.shouldReportEngagement()

        testee.onOpen(isFreshLaunch = false)

        assertTrue(testee.shouldReportEngagement())
    }
}
