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

/**
 * The parsed `usageLimits` entry the Duck.ai page writes into native storage. Native renders
 * [notice] and runs [cta] as supplied.
 */
data class UsageLimitsSnapshot(
    val notice: UsageNotice,
    val cta: UsageCta?,
)

data class UsageNotice(
    val id: UsageNoticeId,
    val window: UsageWindow,
    val percentUsed: Int,
    val resetsAtMillis: Long,
    val reached: Boolean,
    val dismissible: Boolean,
)

enum class UsageNoticeId(val jsonId: String) {
    APPROACHING("approaching"),
    FREE_REACHED("freeReached"),
    DAILY_REACHED("dailyReached"),
    WEEKLY_REACHED_DEGRADED("weeklyReachedDegraded"),
    WEEKLY_REACHED("weeklyReached"),
    ;

    companion object {
        fun fromJsonId(id: String?): UsageNoticeId? = entries.firstOrNull { it.jsonId == id }
    }
}

enum class UsageWindow(val jsonId: String) {
    DAILY("daily"),
    WEEKLY("weekly"),
    ;

    companion object {
        fun fromJsonId(id: String?): UsageWindow? = entries.firstOrNull { it.jsonId == id }
    }
}

data class UsageCta(
    val id: UsageCtaId,
    val modelId: String?,
    val modelIds: List<String>,
    val byModelId: Map<String, UsageCtaModelTargets>,
    val putEntries: List<UsageCtaPutEntry>,
)

enum class UsageCtaId(val jsonId: String) {
    SWITCH_TO_CHEAPER("switchToCheaper"),
    SWITCH_TO_FREE("switchToFree"),
    BYPASS_WEEKLY("bypassWeekly"),
    SUBSCRIBE("subscribe"),
    ;

    companion object {
        fun fromJsonId(id: String?): UsageCtaId? = entries.firstOrNull { it.jsonId == id }
    }
}

data class UsageCtaModelTargets(
    val modelId: String?,
    val modelIds: List<String>,
)

data class UsageCtaPutEntry(
    val key: String,
    val value: String,
)
