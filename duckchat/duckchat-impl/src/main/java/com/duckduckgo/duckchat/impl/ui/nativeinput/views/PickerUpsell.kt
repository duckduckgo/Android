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

import androidx.annotation.StringRes
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.UserTier
import logcat.logcat

enum class PickerSurface(val origin: String) {
    MODEL_PICKER_ADDRESS_BAR("funnel_addressbar_android__modelpicker"),
    MODEL_PICKER_DUCK_AI_TAB("funnel_duckai_android__modelpicker"),
    REASONING_PICKER_ADDRESS_BAR("funnel_addressbar_android__reasoningdropdown"),
    REASONING_PICKER_DUCK_AI_TAB("funnel_duckai_android__reasoningdropdown"),
}

/** Pixel `source` values for the two pickers that can trigger an upsell. */
internal const val UPSELL_SOURCE_MODEL_PICKER = "model_picker"
internal const val UPSELL_SOURCE_REASONING_PICKER = "reasoning_picker"

/** Subscription origin for the FE model-recovery ("switch model") flow */
const val SWITCH_MODEL_ORIGIN = "funnel_duckai_android__switchmodel"

sealed class UpsellCommand {
    data class LaunchPurchase(val origin: String) : UpsellCommand()
    data class LaunchUpgrade(val origin: String) : UpsellCommand()
}

internal fun UserTier.toParam(): String = when (this) {
    UserTier.FREE -> "free"
    UserTier.PLUS -> "plus"
    UserTier.PRO -> "pro"
}

internal fun UpsellCommand.toFlowTypeParam(): String = when (this) {
    is UpsellCommand.LaunchPurchase -> "purchase"
    is UpsellCommand.LaunchUpgrade -> "upgrade"
}

/**
 * Maps a (userTier, requiredTier) pair to the upsell flow that should fire, or `null` when no
 * native subscription flow applies: the user can't purchase a subscription ([isEligible] is false),
 * FREE-required gating, or a tier transition we don't route.
 */
internal fun routeUpsell(
    userTier: UserTier,
    requiredTier: UserTier,
    origin: String,
    isEligible: Boolean,
): UpsellCommand? = when {
    !isEligible -> null
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

/** The gated section's header, paired with the value reported in the upsell impression pixel. */
enum class GatedHeader(@StringRes val titleRes: Int, val pixelValue: String) {
    TRY_FREE_TRIAL(R.string.duckAiModelPickerTryFreeTrial, "try_free_trial"),
    SUBSCRIBER_EXCLUSIVE(R.string.duckAiModelPickerSubscriberExclusive, "subscriber_exclusive"),
    PRO_EXCLUSIVE(R.string.duckAiModelPickerProExclusive, "pro_exclusive"),
}

/**
 * What the user has to do to reach a picker's gated rows. Pro wins when every gated row needs Pro,
 * since neither a trial nor a Plus plan would unlock them.
 */
internal fun gatedSectionHeader(
    requiredTiers: List<UserTier?>,
    isFreeTrialEligible: Boolean,
): GatedHeader = when {
    requiredTiers.all { it == UserTier.PRO } -> GatedHeader.PRO_EXCLUSIVE
    isFreeTrialEligible -> GatedHeader.TRY_FREE_TRIAL
    else -> GatedHeader.SUBSCRIBER_EXCLUSIVE
}
