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

package com.duckduckgo.newtabpage.api

/**
 * Tracks whether the most recent NTP display was triggered by an idle timeout or by the user,
 * and fires the appropriate pixels for all NTP after-idle interactions.
 */
interface NtpAfterIdleManager {
    /** Returns true if the last NTP was shown because the idle threshold was met. */
    fun wasAfterIdle(): Boolean

    /** Records that the NTP was shown due to an idle timeout and fires the shown pixels. */
    fun onNtpShownAfterIdle()

    /** Records that the NTP was shown by user action and fires the shown pixels. */
    fun onNtpShownUserInitiated()

    /** Fires the return-to-page-tapped pixel using the correct after-idle / user-initiated context. */
    fun fireReturnToPageTapped()

    /** Fires the search-bar-used-from-NTP pixel using the correct after-idle / user-initiated context. */
    fun fireBarUsedFromNtp()

    /** Fires the timeout-selected pixel for the given [seconds] value. No-op if the value is unknown. */
    fun fireTimeoutSelected(seconds: Long)
}
