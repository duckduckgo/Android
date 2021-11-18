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

package com.duckduckgo.app.settings.extension

import android.content.Context
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds
import javax.inject.Singleton

interface InternalFeaturePlugin {
    /** @return the title of the internal feature */
    fun internalFeatureTitle(): String

    /** @return return the subtitle of the feature or null */
    fun internalFeatureSubtitle(): String

    /**
     * This method will be called when the user clicks on the feature
     *
     * [activityContext] is the Activity context that hosted the feature
     */
    fun onInternalFeatureClicked(activityContext: Context)
}

private class SettingsInternalFeaturePluginPoint(
    private val plugins: Set<@JvmSuppressWildcards InternalFeaturePlugin>
) : PluginPoint<InternalFeaturePlugin> {
    override fun getPlugins(): Collection<InternalFeaturePlugin> {
        return plugins.sortedBy { it.internalFeatureTitle() }
    }
}

@Module
@ContributesTo(AppObjectGraph::class)
abstract class SettingInternalFeaturePluginModule {
    @Multibinds
    abstract fun bindEmptySettingInternalFeaturePlugins(): Set<@JvmSuppressWildcards InternalFeaturePlugin>

    @Module
    @ContributesTo(AppObjectGraph::class)
    class SettingInternalFeaturePluginModuleExt {
        @Provides
        @Singleton
        fun provideSettingInternalFeaturePlugins(
            plugins: Set<@JvmSuppressWildcards InternalFeaturePlugin>
        ): PluginPoint<InternalFeaturePlugin> {
            return SettingsInternalFeaturePluginPoint(plugins)
        }
    }
}
