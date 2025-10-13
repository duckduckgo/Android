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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.contentscopescripts.api.contentscopeExperiments.ContentScopeExperiments
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealContentScopeExperiments @Inject constructor(
    private val contentScopeExperimentsFeature: ContentScopeExperimentsFeature,
    private val featureTogglesInventory: FeatureTogglesInventory,
    private val dispatcherProvider: DispatcherProvider,
) : ContentScopeExperiments {
    override suspend fun getActiveExperiments(): List<Toggle> =
        withContext(dispatcherProvider.io()) {
            val featureName = contentScopeExperimentsFeature.self().featureName().name
            val experiments =
                if (contentScopeExperimentsFeature.self().isEnabled()) {
                    featureTogglesInventory.getAllTogglesForParent(featureName)
                } else {
                    emptyList()
                }
            experiments.mapNotNull {
                it.enroll()
                if (it.isEnabled()) {
                    it
                } else {
                    null
                }
            }
        }
}
