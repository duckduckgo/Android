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

package com.duckduckgo.pir.impl.rmf

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.freemium.PirFreemium
import com.duckduckgo.pir.impl.freemium.PirFreemiumState
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import com.duckduckgo.pir.impl.store.PirFreemiumFirstScanResult
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class PirFreemiumAttributeMatcherPlugin @Inject constructor(
    private val pirFreemium: PirFreemium,
    private val pirFreemiumDataStore: PirFreemiumDataStore,
) : AttributeMatcherPlugin {
    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        return when (matchingAttribute) {
            is PirFreemiumEligibleJsonMatchingAttribute -> {
                matchingAttribute.remoteValue == (pirFreemium.getPirFreemiumState() != PirFreemiumState.NOT_ELIGIBLE)
            }

            is PirFreemiumDidActivateJsonMatchingAttribute -> {
                matchingAttribute.remoteValue == pirFreemiumDataStore.didActivate
            }

            is PirFreemiumFirstScanResultJsonMatchingAttribute -> {
                // No outcome yet must never match: a user whose scan is still running would be told nothing was found.
                val firstScanResult = pirFreemiumDataStore.firstScanResult ?: return false
                matchingAttribute.remoteValue.equals(firstScanResult.rmfValue, ignoreCase = true)
            }

            else -> null
        }
    }

    // Spelled out rather than derived from the enum names: the values are shared with the iOS config.
    private val PirFreemiumFirstScanResult.rmfValue: String
        get() = when (this) {
            PirFreemiumFirstScanResult.NO_MATCHES -> "noMatches"
            PirFreemiumFirstScanResult.MATCHES_FOUND -> "matchesFound"
        }
}
