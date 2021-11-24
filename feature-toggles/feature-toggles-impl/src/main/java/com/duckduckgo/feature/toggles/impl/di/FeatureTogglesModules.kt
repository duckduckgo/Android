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

package com.duckduckgo.feature.toggles.impl.di

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.feature.toggles.api.FeatureTogglesPlugin
import com.duckduckgo.feature.toggles.impl.FeatureCustomConfigPluginPoint
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds
import dagger.SingleIn

@Module
@ContributesTo(AppObjectGraph::class)
abstract class FeatureTogglesBindingModule {

    @Multibinds
    abstract fun provideFeatureTogglesPlugins(): Set<@JvmSuppressWildcards FeatureTogglesPlugin>

}

@Module
@ContributesTo(AppObjectGraph::class)
class FeatureTogglesModule {

    @Provides
    @SingleIn(AppObjectGraph::class)
    fun provideFeatureTogglesPluginPoint(toggles: Set<@JvmSuppressWildcards FeatureTogglesPlugin>): PluginPoint<FeatureTogglesPlugin> {
        return FeatureCustomConfigPluginPoint(toggles)
    }
}
