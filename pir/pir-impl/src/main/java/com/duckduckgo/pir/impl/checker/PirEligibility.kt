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

package com.duckduckgo.pir.impl.checker

sealed interface PirEligibility {
    data class Enabled(val runMode: PirRunMode) : PirEligibility
    data class Disabled(val reason: DisabledReason) : PirEligibility
}

/**
 * What work an eligible user is allowed to run.
 */
enum class PirRunMode {
    SCAN_AND_OPT_OUT,

    /** A freemium user: scans only, never opt-outs and never repeat maintenance scans. */
    SCAN_ONLY,
}

enum class DisabledReason {
    FEATURE_DISABLED,
    SUBSCRIPTION_EXPIRED,
    ENTITLEMENT_LOST,
    REPOSITORY_UNAVAILABLE,
}

val PirEligibility.isEnabled: Boolean
    get() = this is PirEligibility.Enabled
