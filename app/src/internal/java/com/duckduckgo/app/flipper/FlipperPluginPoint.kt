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

package com.duckduckgo.app.flipper

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.facebook.flipper.core.FlipperPlugin
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds
import dagger.SingleInstanceIn

private class FlipperPluginPluginPoint(
    private val plugins: DaggerSet<FlipperPlugin>
) : PluginPoint<FlipperPlugin> {
    override fun getPlugins(): Collection<FlipperPlugin> {
        return plugins
    }
}

@Module
@ContributesTo(AppScope::class)
abstract class FlipperPluginModule {
    @Multibinds
    abstract fun bindEmptySettingInternalFeaturePlugins(): DaggerSet<FlipperPlugin>

    @Module
    @ContributesTo(AppScope::class)
    class SettingInternalFeaturePluginModuleExt {
        @Provides
        @SingleInstanceIn(AppScope::class)
        fun provideSettingInternalFeaturePlugins(
            plugins: DaggerSet<FlipperPlugin>
        ): PluginPoint<FlipperPlugin> {
            return FlipperPluginPluginPoint(plugins)
        }
    }
}
