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

data class UsageNoticeDismissal(
    val noticeId: UsageNoticeId,
    val window: UsageWindow,
    val resetsAtMillis: Long,
    val band: Int,
)

object UsageNoticeBand {
    private val THRESHOLDS = listOf(90, 75, 50)

    fun of(percentUsed: Int): Int = THRESHOLDS.firstOrNull { percentUsed >= it } ?: 0
}

object UsageNoticeDismissalPolicy {
    fun isSuppressed(
        notice: UsageNotice,
        dismissal: UsageNoticeDismissal?,
    ): Boolean {
        if (notice.reached || dismissal == null) return false
        return dismissal.noticeId == notice.id &&
            dismissal.window == notice.window &&
            dismissal.resetsAtMillis == notice.resetsAtMillis &&
            UsageNoticeBand.of(notice.percentUsed) <= dismissal.band
    }
}
