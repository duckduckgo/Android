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

package com.duckduckgo.app.browser.returnsession

enum class ReturnSessionLanding(val value: String) {
    NTP("ntp"),
    NTP_USER_INITIATED("ntp_user_initiated"),
    WEB("web"),
    SERP("serp"),
    DUCK_AI("duck_ai"),
}

data class ReturnSessionLandingResult(
    val afterIdle: Boolean,
    val landing: ReturnSessionLanding,
)

interface ReturnSessionLandingListener {
    /** Starts telemetry after the opening-screen pipeline has applied its final destination. */
    fun onReturnLandingResolved(result: ReturnSessionLandingResult)

    fun onLandingFocusCaptured(focused: Boolean)

    fun onReturnClosed()
}
