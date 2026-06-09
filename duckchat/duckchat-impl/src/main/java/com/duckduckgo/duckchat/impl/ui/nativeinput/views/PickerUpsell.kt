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

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import com.duckduckgo.duckchat.impl.models.UserTier
import logcat.logcat

enum class PickerSurface(val origin: String) {
    MODEL_PICKER_ADDRESS_BAR("funnel_nativeinput_androidapp__modelpicker"),
    MODEL_PICKER_DUCK_AI_TAB("funnel_duckai_androidapp__modelpicker"),
    REASONING_PICKER_ADDRESS_BAR("funnel_nativeinput_androidapp__reasoningpicker"),
    REASONING_PICKER_DUCK_AI_TAB("funnel_duckai_androidapp__reasoningpicker"),
}

sealed class UpsellCommand {
    data class LaunchPurchase(val origin: String) : UpsellCommand()
    data class LaunchUpgrade(val origin: String) : UpsellCommand()
}

/**
 * Maps a (userTier, requiredTier) pair to the upsell flow that should fire, or `null` when no
 * native subscription flow applies (FREE-required gating, or a tier transition we don't route).
 */
internal fun routeUpsell(
    userTier: UserTier,
    requiredTier: UserTier,
    origin: String,
): UpsellCommand? = when {
    requiredTier == UserTier.FREE -> null
    userTier == UserTier.FREE -> UpsellCommand.LaunchPurchase(origin)
    userTier == UserTier.PLUS && requiredTier == UserTier.PRO -> UpsellCommand.LaunchUpgrade(origin)
    else -> {
        // Only reachable on data inconsistency. The user already covers the required tier so the
        // upsell shouldn't have been requested. Logging a breadcrumb for tracking.
        logcat(tag = "PickerUpsell") {
            "Duck.ai upsell: no native subscription flow for tap (userTier=$userTier, requiredTier=$requiredTier, origin=$origin)"
        }
        null
    }
}
