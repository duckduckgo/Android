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

package com.duckduckgo.agesignals.impl.dev

import android.content.Context
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.internal.features.api.InternalFeaturePlugin
import com.duckduckgo.navigation.api.GlobalActivityStarter
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ElementsIntoSet
import dev.zacsweers.metro.Provides
import javax.inject.Inject

class AgeSignalsInternalFeaturePlugin @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
) : InternalFeaturePlugin {
    override fun internalFeatureTitle(): String = "Age Signals"

    override fun internalFeatureSubtitle(): String = "Run the Play Age Signals check and simulate its results"

    override fun onInternalFeatureClicked(activityContext: Context) {
        globalActivityStarter.start(activityContext, AgeSignalsDevSettingsScreen)
    }
}

/**
 * This module ships in play builds too, and settings shows its internal section whenever any
 * [InternalFeaturePlugin] is bound, so the plugin is only contributed on internal builds.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object AgeSignalsInternalFeaturePluginModule {
    @Provides
    @ElementsIntoSet
    fun provideInternalFeaturePlugins(
        appBuildConfig: AppBuildConfig,
        plugin: AgeSignalsInternalFeaturePlugin,
    ): Set<InternalFeaturePlugin> = if (appBuildConfig.flavor == BuildFlavor.INTERNAL) setOf(plugin) else emptySet()
}
