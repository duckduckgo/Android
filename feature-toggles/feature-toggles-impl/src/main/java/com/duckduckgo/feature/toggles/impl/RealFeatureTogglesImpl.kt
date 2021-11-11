/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.feature.toggles.impl

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.feature.toggles.api.FeatureName
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.feature.toggles.api.FeatureTogglesPlugin
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import javax.inject.Singleton

@ContributesBinding(AppObjectGraph::class)
@Singleton
class RealFeatureToggleImpl
@Inject
constructor(private val featureTogglesPluginPoint: PluginPoint<FeatureTogglesPlugin>) :
    FeatureToggle {

    override fun isFeatureEnabled(featureName: FeatureName, defaultValue: Boolean): Boolean? {
        featureTogglesPluginPoint.getPlugins().forEach { plugin ->
            plugin.isEnabled(featureName, defaultValue)?.let {
                return it
            }
        }
        return null
    }
}

class FeatureCustomConfigPluginPoint(
    private val toggles: Set<@JvmSuppressWildcards FeatureTogglesPlugin>
) : PluginPoint<FeatureTogglesPlugin> {
    override fun getPlugins(): Collection<FeatureTogglesPlugin> {
        return toggles
    }
}
