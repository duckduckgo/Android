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

import kotlinx.coroutines.flow.StateFlow

/**
 * Handles events related to the NTP-after-idle feature.
 */
interface NtpAfterIdleManager {
    /** Called when an idle-triggered NTP is about to be rendered. Must be invoked before the NTP appears. */
    fun onIdleReturnTriggered()

    /** Called when an NTP becomes visible to the user. */
    fun onNtpShown()

    /** Called when the user taps the return-to-page hatch on the NTP. */
    fun onReturnToPageTapped()

    /** Called when the user submits a search from the NTP. */
    fun onNtpSearchSubmitted()

    /** Called when the user selects an idle timeout value in seconds. */
    fun onIdleTimeoutSelected(seconds: Long)

    /** Emits true when the current NTP was shown as a result of an idle return, false otherwise. */
    val isAfterIdleReturn: StateFlow<Boolean>
}
