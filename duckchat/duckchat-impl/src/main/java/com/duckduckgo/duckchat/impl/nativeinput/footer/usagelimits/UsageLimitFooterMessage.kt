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
import com.duckduckgo.duckchat.impl.R
import javax.inject.Inject
import kotlin.math.ceil

data class UsageLimitFooterMessage(
    val title: String,
    val resetText: String,
    val icon: Icon,
    val dismissible: Boolean,
    val ctaLabel: String? = null,
) {
    sealed class Icon {
        data class Ring(
            val progress: Float,
            val severity: Severity,
        ) : Icon()

        data object Alert : Icon()
    }

    enum class Severity { INFO, WARNING, CRITICAL }
}

class UsageLimitFooterMessageMapper @Inject constructor() {

    fun map(
        notice: UsageNotice,
        nowMillis: Long,
        resources: Resources,
        resolvedCta: ResolvedUsageCta? = null,
    ): UsageLimitFooterMessage = UsageLimitFooterMessage(
        title = title(notice, resources),
        resetText = resources.getString(R.string.duckChatUsageLimitFooterResetsIn, remaining(notice.resetsAtMillis - nowMillis, resources)),
        icon = if (notice.reached) UsageLimitFooterMessage.Icon.Alert else ring(notice.percentUsed),
        dismissible = notice.dismissible && !notice.reached,
        ctaLabel = resolvedCta?.let { ctaLabel(it, resources) },
    )

    private fun ctaLabel(
        cta: ResolvedUsageCta,
        resources: Resources,
    ): String = when (cta) {
        is ResolvedUsageCta.SwitchModel -> resources.getString(R.string.duckChatUsageLimitFooterCtaSwitchModel)
        is ResolvedUsageCta.StartUsingWeeklyLimit -> resources.getString(R.string.duckChatUsageLimitFooterCtaStartUsingWeeklyLimit)
        is ResolvedUsageCta.Subscribe ->
            if (cta.freeTrialEligible) {
                resources.getString(R.string.duckChatUsageLimitFooterCtaTryForFree)
            } else {
                resources.getString(R.string.duckChatUsageLimitFooterCtaSubscribe)
            }
    }

    private fun title(
        notice: UsageNotice,
        resources: Resources,
    ): String = when (notice.id) {
        UsageNoticeId.APPROACHING -> when (notice.window) {
            UsageWindow.DAILY -> resources.getString(R.string.duckChatUsageLimitFooterApproachingDaily, notice.percentUsed)
            UsageWindow.WEEKLY -> resources.getString(R.string.duckChatUsageLimitFooterApproachingWeekly, notice.percentUsed)
        }
        UsageNoticeId.DAILY_REACHED -> resources.getString(R.string.duckChatUsageLimitFooterDailyReached)
        UsageNoticeId.FREE_REACHED -> when (notice.window) {
            UsageWindow.DAILY -> resources.getString(R.string.duckChatUsageLimitFooterDailyReached)
            UsageWindow.WEEKLY -> resources.getString(R.string.duckChatUsageLimitFooterWeeklyReachedFree)
        }
        UsageNoticeId.WEEKLY_REACHED_DEGRADED -> resources.getString(R.string.duckChatUsageLimitFooterAdvancedModelsReached)
        UsageNoticeId.WEEKLY_REACHED -> resources.getString(R.string.duckChatUsageLimitFooterWeeklyReached)
    }

    private fun ring(percentUsed: Int) = UsageLimitFooterMessage.Icon.Ring(
        progress = percentUsed / 100f,
        severity = when (UsageNoticeBand.of(percentUsed)) {
            90 -> UsageLimitFooterMessage.Severity.CRITICAL
            75 -> UsageLimitFooterMessage.Severity.WARNING
            else -> UsageLimitFooterMessage.Severity.INFO
        },
    )

    // Rounding up (25h reads as "2 days" and 23.9h as "24 hours") with a "one hour" floor so it never reads "0 hours".
    private fun remaining(
        remainingMillis: Long,
        resources: Resources,
    ): String {
        if (remainingMillis >= DAY_MILLIS) {
            val days = ceil(remainingMillis / DAY_MILLIS.toDouble()).toInt()
            return resources.getQuantityString(R.plurals.duckChatUsageLimitFooterDays, days, days)
        }
        val hours = ceil(remainingMillis / HOUR_MILLIS.toDouble()).toInt().coerceAtLeast(1)
        return resources.getQuantityString(R.plurals.duckChatUsageLimitFooterHours, hours, hours)
    }

    private companion object {
        const val HOUR_MILLIS = 60L * 60L * 1000L
        const val DAY_MILLIS = 24L * HOUR_MILLIS
    }
}
