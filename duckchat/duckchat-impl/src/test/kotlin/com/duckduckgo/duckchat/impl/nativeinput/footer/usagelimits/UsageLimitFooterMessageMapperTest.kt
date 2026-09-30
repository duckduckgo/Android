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

import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageLimitFooterMessage.Icon
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageLimitFooterMessage.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsageLimitFooterMessageMapperTest {

    private val resources: Resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val testee = UsageLimitFooterMessageMapper()

    @Test
    fun whenApproachingThenTitleCarriesPercentAndWindow() {
        assertEquals("75% of weekly limit", map(notice(UsageNoticeId.APPROACHING, UsageWindow.WEEKLY, 75)).title)
        assertEquals("50% of daily limit", map(notice(UsageNoticeId.APPROACHING, UsageWindow.DAILY, 50)).title)
    }

    @Test
    fun whenLimitIsReachedThenTitleFollowsTheNoticeId() {
        assertEquals("Daily limit reached", map(reached(UsageNoticeId.DAILY_REACHED, UsageWindow.DAILY)).title)
        assertEquals("Daily limit reached", map(reached(UsageNoticeId.FREE_REACHED, UsageWindow.DAILY)).title)
        assertEquals("Weekly limit reached", map(reached(UsageNoticeId.FREE_REACHED, UsageWindow.WEEKLY)).title)
        assertEquals("Advanced AI models limit reached", map(reached(UsageNoticeId.WEEKLY_REACHED_DEGRADED, UsageWindow.WEEKLY)).title)
        assertEquals("Weekly usage limit reached", map(reached(UsageNoticeId.WEEKLY_REACHED, UsageWindow.WEEKLY)).title)
    }

    @Test
    fun whenApproachingThenRingSeverityFollowsTheBand() {
        assertEquals(Icon.Ring(0.5f, Severity.INFO), map(notice(UsageNoticeId.APPROACHING, UsageWindow.WEEKLY, 50)).icon)
        assertEquals(Icon.Ring(0.74f, Severity.INFO), map(notice(UsageNoticeId.APPROACHING, UsageWindow.WEEKLY, 74)).icon)
        assertEquals(Icon.Ring(0.75f, Severity.WARNING), map(notice(UsageNoticeId.APPROACHING, UsageWindow.WEEKLY, 75)).icon)
        assertEquals(Icon.Ring(0.9f, Severity.CRITICAL), map(notice(UsageNoticeId.APPROACHING, UsageWindow.WEEKLY, 90)).icon)
    }

    @Test
    fun whenLimitIsReachedThenAlertIconIsUsedAndNotDismissible() {
        val message = map(reached(UsageNoticeId.WEEKLY_REACHED, UsageWindow.WEEKLY))

        assertEquals(Icon.Alert, message.icon)
        assertFalse(message.dismissible)
    }

    @Test
    fun whenApproachingThenDismissibleFollowsTheNotice() {
        assertTrue(map(notice(UsageNoticeId.APPROACHING, UsageWindow.WEEKLY, 50)).dismissible)
        assertFalse(map(notice(UsageNoticeId.APPROACHING, UsageWindow.WEEKLY, 50).copy(dismissible = false)).dismissible)
    }

    @Test
    fun whenMoreThanADayRemainsThenResetTextIsInWholeDaysRoundedUp() {
        assertEquals("Resets in 2 days", map(notice(resetsIn = 2 * DAY)).resetText)
        assertEquals("Resets in 2 days", map(notice(resetsIn = DAY + HOUR)).resetText)
        assertEquals("Resets in 1 day", map(notice(resetsIn = DAY)).resetText)
    }

    @Test
    fun whenLessThanADayRemainsThenResetTextIsInWholeHoursRoundedUp() {
        assertEquals("Resets in 12 hours", map(notice(resetsIn = 12 * HOUR)).resetText)
        assertEquals("Resets in 6 hours", map(notice(resetsIn = 5 * HOUR + 1)).resetText)
        assertEquals("Resets in 1 hour", map(notice(resetsIn = HOUR)).resetText)
    }

    @Test
    fun whenAlmostNoTimeRemainsThenResetTextFloorsAtOneHour() {
        assertEquals("Resets in 1 hour", map(notice(resetsIn = 1)).resetText)
        assertEquals("Resets in 1 hour", map(notice(resetsIn = 0)).resetText)
    }

    @Test
    fun whenCtaIsResolvedThenLabelFollowsItsKind() {
        val notice = notice()
        val model = AIChatModel(id = "m", name = "m", displayName = "m", shortName = "m", accessTier = emptyList(), isAccessible = true)

        assertEquals("Switch Model", testee.map(notice, NOW, resources, ResolvedUsageCta.SwitchModel(model, listOf("m"))).ctaLabel)
        assertEquals("Start using weekly limit", testee.map(notice, NOW, resources, ResolvedUsageCta.StartUsingWeeklyLimit(emptyList())).ctaLabel)
        assertEquals("Try for Free", testee.map(notice, NOW, resources, ResolvedUsageCta.Subscribe(freeTrialEligible = true)).ctaLabel)
        assertEquals("Subscribe", testee.map(notice, NOW, resources, ResolvedUsageCta.Subscribe(freeTrialEligible = false)).ctaLabel)
        assertNull(testee.map(notice, NOW, resources, null).ctaLabel)
    }

    private fun map(notice: UsageNotice) = testee.map(notice, NOW, resources)

    private fun notice(
        id: UsageNoticeId = UsageNoticeId.APPROACHING,
        window: UsageWindow = UsageWindow.WEEKLY,
        percent: Int = 50,
        resetsIn: Long = 2 * DAY,
    ) = UsageNotice(
        id = id,
        window = window,
        percentUsed = percent,
        resetsAtMillis = NOW + resetsIn,
        reached = false,
        dismissible = true,
    )

    private fun reached(
        id: UsageNoticeId,
        window: UsageWindow,
    ) = notice(id, window, 100).copy(reached = true, dismissible = false)

    private companion object {
        const val NOW = 1787659200000L
        const val HOUR = 60L * 60L * 1000L
        const val DAY = 24L * HOUR
    }
}
