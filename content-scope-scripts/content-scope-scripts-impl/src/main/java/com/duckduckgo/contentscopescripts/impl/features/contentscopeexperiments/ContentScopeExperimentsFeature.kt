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

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "contentScopeExperiments",
)
interface ContentScopeExperimentsFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun bloops(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun test(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment0(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment1(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment2(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment3(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment4(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment5(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment6(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment7(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment8(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment9(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment10(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment11(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment12(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment13(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment14(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment15(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment16(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment17(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment18(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun contentScopeExperiment19(): Toggle

    enum class Cohorts(override val cohortName: String) : CohortName {
        CONTROL("control"),
        TREATMENT("treatment"),
    }
}
