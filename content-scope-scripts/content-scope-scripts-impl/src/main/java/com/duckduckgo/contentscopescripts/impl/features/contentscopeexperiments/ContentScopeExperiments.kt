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

package com.duckduckgo.contentscopescripts.impl.features.contentscopeexperiments

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi.Builder
import com.squareup.moshi.Types
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

interface ContentScopeExperiments {
    fun getExperimentsJson(): String
}

@ContributesBinding(AppScope::class)
class RealContentScopeExperiments @Inject constructor(
    private val contentScopeExperimentsFeature: ContentScopeExperimentsFeature,
    private val featureTogglesInventory: FeatureTogglesInventory,
) : ContentScopeExperiments {

    override fun getExperimentsJson(): String {
        val featureName = contentScopeExperimentsFeature.self().featureName().name
        val type = Types.newParameterizedType(List::class.java, Experiment::class.java)
        val moshi = Builder().build()
        val jsonAdapter: JsonAdapter<List<Experiment>> = moshi.adapter(type)
        return runBlocking {
            val experiments = if (contentScopeExperimentsFeature.self().isEnabled()) {
                featureTogglesInventory.getAllTogglesForParent(featureName)
            } else {
                emptyList()
            }
            experiments.mapNotNull {
                it.enroll()
                if (it.isEnabled()) {
                    Experiment(
                        cohort = it.getCohort()?.name,
                        feature = featureName,
                        subfeature = it.featureName().name,
                    )
                } else {
                    null
                }
            }.let {
                jsonAdapter.toJson(it)
            }
        }
    }

    private data class Experiment(
        val feature: String,
        val subfeature: String,
        val cohort: String?,
    )
}
