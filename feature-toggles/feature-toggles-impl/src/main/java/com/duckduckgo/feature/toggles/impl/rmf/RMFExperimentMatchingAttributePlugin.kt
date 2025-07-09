/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.feature.toggles.impl.rmf

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.remote.messaging.api.AttributeMatcherPlugin
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = JsonToMatchingAttributeMapper::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = AttributeMatcherPlugin::class,
)
@SingleInstanceIn(AppScope::class)
class RMFExperimentMatchingAttributePlugin @Inject constructor(
    private val featureTogglesInventory: FeatureTogglesInventory,
) : JsonToMatchingAttributeMapper, AttributeMatcherPlugin {

    override fun map(
        key: String,
        jsonMatchingAttribute: JsonMatchingAttribute,
    ): MatchingAttribute? = if (key == ExperimentMatchingAttribute.KEY) {
        val value = jsonMatchingAttribute.value as? List<String>
        value.takeUnless { it.isNullOrEmpty() }?.let { featureFlags ->
            ExperimentMatchingAttribute(featureFlags)
        }
    } else {
        null
    }

    override suspend fun evaluate(matchingAttribute: MatchingAttribute): Boolean? {
        return when (matchingAttribute) {
            is ExperimentMatchingAttribute -> {
                assert(matchingAttribute.values.isNotEmpty())
                val activeExperimentName = featureTogglesInventory.getAllActiveExperimentToggles().map { it.featureName().name }
                return matchingAttribute.values.any { activeExperimentName.contains(it) }
            }

            else -> null
        }
    }
}

data class ExperimentMatchingAttribute(
    val values: List<String>,
) : MatchingAttribute {
    companion object {
        const val KEY = "isUserInAnyActiveExperiment"
    }
}
