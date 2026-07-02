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

package com.duckduckgo.app.fire.promo

import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * Supplies the matching attributes the Fire Tabs promo RMF message is gated on:
 *  - `fireModeAvailable` — whether Fire Mode is available on this device/build.
 *  - `fireModeUsed` — whether the user has ever entered Fire Mode (used in exclusionRules).
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
class FireTabsPromoAttributeMatcher @Inject constructor(
    private val fireModeAvailability: FireModeAvailability,
    private val fireDataStore: FireDataStore,
) : JsonToMatchingAttributeMapper, AttributeMatcherPlugin {

    override fun map(
        key: String,
        jsonMatchingAttribute: JsonMatchingAttribute,
    ): MatchingAttribute? {
        return when (key) {
            ATTRIBUTE_FIRE_MODE_AVAILABLE -> (jsonMatchingAttribute.value as? Boolean)?.let { FireModeAvailableMatchingAttribute(it) }
            ATTRIBUTE_USED_FIRE_MODE -> (jsonMatchingAttribute.value as? Boolean)?.let { UsedFireModeMatchingAttribute(it) }
            else -> null
        }
    }

    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        return when (matchingAttribute) {
            is FireModeAvailableMatchingAttribute -> fireModeAvailability.isAvailable() == matchingAttribute.value
            is UsedFireModeMatchingAttribute -> fireDataStore.hasUsedFireMode() == matchingAttribute.value
            else -> null
        }
    }

    companion object {
        internal const val ATTRIBUTE_FIRE_MODE_AVAILABLE = "fireModeAvailable"
        internal const val ATTRIBUTE_USED_FIRE_MODE = "fireModeUsed"
    }
}

data class FireModeAvailableMatchingAttribute(val value: Boolean) : MatchingAttribute

data class UsedFireModeMatchingAttribute(val value: Boolean) : MatchingAttribute
