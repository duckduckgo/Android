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

package com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageNoticeDismissalPolicyTest {

    @Test
    fun whenPercentIsMappedToBandThenThresholdsApply() {
        assertEquals(0, UsageNoticeBand.of(0))
        assertEquals(0, UsageNoticeBand.of(49))
        assertEquals(50, UsageNoticeBand.of(50))
        assertEquals(50, UsageNoticeBand.of(74))
        assertEquals(75, UsageNoticeBand.of(75))
        assertEquals(75, UsageNoticeBand.of(89))
        assertEquals(90, UsageNoticeBand.of(90))
        assertEquals(90, UsageNoticeBand.of(100))
    }

    @Test
    fun whenNothingWasDismissedThenNoticeIsNotSuppressed() {
        assertFalse(UsageNoticeDismissalPolicy.isSuppressed(approaching(55), null))
    }

    @Test
    fun whenSameNoticeInSameBandThenItIsSuppressed() {
        val dismissal = dismissalOf(approaching(52))

        assertTrue(UsageNoticeDismissalPolicy.isSuppressed(approaching(52), dismissal))
        assertTrue(UsageNoticeDismissalPolicy.isSuppressed(approaching(70), dismissal))
    }

    @Test
    fun whenUsageClimbsIntoAHigherBandThenNoticeReturns() {
        val dismissal = dismissalOf(approaching(52))

        assertFalse(UsageNoticeDismissalPolicy.isSuppressed(approaching(75), dismissal))
        assertFalse(UsageNoticeDismissalPolicy.isSuppressed(approaching(90), dismissal))
    }

    @Test
    fun whenUsageDropsToALowerBandThenNoticeStaysSuppressed() {
        assertTrue(UsageNoticeDismissalPolicy.isSuppressed(approaching(60), dismissalOf(approaching(80))))
    }

    @Test
    fun whenLimitIsReachedThenNoticeIsNeverSuppressed() {
        val reached = approaching(100).copy(id = UsageNoticeId.DAILY_REACHED, reached = true, dismissible = false)

        assertFalse(UsageNoticeDismissalPolicy.isSuppressed(reached, dismissalOf(approaching(90))))
    }

    @Test
    fun whenWindowResetsThenNoticeReturns() {
        val dismissal = dismissalOf(approaching(80))

        assertFalse(UsageNoticeDismissalPolicy.isSuppressed(approaching(80).copy(resetsAtMillis = RESETS_AT + 1), dismissal))
    }

    @Test
    fun whenNoticeIsForAnotherWindowOrIdThenItIsNotSuppressed() {
        val dismissal = dismissalOf(approaching(80))

        assertFalse(UsageNoticeDismissalPolicy.isSuppressed(approaching(80).copy(window = UsageWindow.DAILY), dismissal))
        assertFalse(UsageNoticeDismissalPolicy.isSuppressed(approaching(80).copy(id = UsageNoticeId.WEEKLY_REACHED_DEGRADED), dismissal))
    }

    private fun approaching(percent: Int) = UsageNotice(
        id = UsageNoticeId.APPROACHING,
        window = UsageWindow.WEEKLY,
        percentUsed = percent,
        resetsAtMillis = RESETS_AT,
        reached = false,
        dismissible = true,
    )

    private fun dismissalOf(notice: UsageNotice) = UsageNoticeDismissal(
        noticeId = notice.id,
        window = notice.window,
        resetsAtMillis = notice.resetsAtMillis,
        band = UsageNoticeBand.of(notice.percentUsed),
    )

    private companion object {
        const val RESETS_AT = 1788134400000L
    }
}
