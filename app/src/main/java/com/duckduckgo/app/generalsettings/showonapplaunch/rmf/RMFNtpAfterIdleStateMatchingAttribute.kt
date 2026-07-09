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

package com.duckduckgo.app.generalsettings.showonapplaunch.rmf

import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.rmf.NtpAfterIdleState.ELIGIBLE_CARD_HIDDEN
import com.duckduckgo.app.generalsettings.showonapplaunch.rmf.NtpAfterIdleState.ELIGIBLE_CARD_SHOWN
import com.duckduckgo.app.generalsettings.showonapplaunch.rmf.NtpAfterIdleState.NOT_ELIGIBLE
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Matches the user's NTP-after-idle state, combining the after-idle "open on launch" option with the
 * return-to-tab card visibility into a single value.
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = JsonToMatchingAttributeMapper::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = AttributeMatcherPlugin::class,
)
@SingleInstanceIn(AppScope::class)
class RMFNtpAfterIdleStateMatchingAttribute @Inject constructor(
    private val showOnAppLaunchOptionDataStore: ShowOnAppLaunchOptionDataStore,
    private val ntpAfterIdleManager: NtpAfterIdleManager,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
) : JsonToMatchingAttributeMapper, AttributeMatcherPlugin {

    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        if (matchingAttribute is NtpAfterIdleStateMatchingAttribute) {
            return currentState() in matchingAttribute.states
        }
        return null
    }

    override fun map(
        key: String,
        jsonMatchingAttribute: JsonMatchingAttribute,
    ): MatchingAttribute? {
        if (key != NtpAfterIdleStateMatchingAttribute.KEY) return null
        // Rollout gate: when either flag is off, leave the attribute unmapped so RMF treats it as Unknown(fallback).
        val rolloutEnabled = androidBrowserConfigFeature.showNTPAfterIdleReturn().isEnabled() &&
            androidBrowserConfigFeature.ntpAsDefaultAfterIdleReturn().isEnabled()
        if (!rolloutEnabled) return null
        val value = jsonMatchingAttribute.value
        if (value is List<*>) {
            val states = value.filterIsInstance<String>()
                .mapNotNull { raw -> NtpAfterIdleState.entries.firstOrNull { it.jsonValue == raw } }
            if (states.isNotEmpty()) {
                return NtpAfterIdleStateMatchingAttribute(states)
            }
        }
        return null
    }

    private suspend fun currentState(): NtpAfterIdleState {
        if (showOnAppLaunchOptionDataStore.optionFlow.firstOrNull() != NewTabPage) return NOT_ELIGIBLE
        return if (ntpAfterIdleManager.returnToLastTabEnabled.firstOrNull() == true) ELIGIBLE_CARD_SHOWN else ELIGIBLE_CARD_HIDDEN
    }
}

internal enum class NtpAfterIdleState(val jsonValue: String) {
    NOT_ELIGIBLE("notEligible"),
    ELIGIBLE_CARD_SHOWN("eligibleCardShown"),
    ELIGIBLE_CARD_HIDDEN("eligibleCardHidden"),
}

internal data class NtpAfterIdleStateMatchingAttribute(
    val states: List<NtpAfterIdleState>,
) : MatchingAttribute {
    companion object {
        const val KEY = "ntpAfterIdleState"
    }
}
