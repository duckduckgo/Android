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

package com.duckduckgo.app.global.api

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.global.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds
import dagger.SingleInstanceIn

private class PixelInterceptorPluginPoint(
    private val plugins: Set<@JvmSuppressWildcards PixelInterceptorPlugin>
) : PluginPoint<PixelInterceptorPlugin> {
    override fun getPlugins(): Collection<PixelInterceptorPlugin> {
        return plugins.sortedBy { it.javaClass.simpleName }
    }
}

@Module
@ContributesTo(AppScope::class)
abstract class PixelInterceptorPluginModule {
    @Multibinds
    abstract fun bindEmptyPixelInterceptorPlugins(): Set<@JvmSuppressWildcards PixelInterceptorPlugin>

    @Module
    @ContributesTo(AppScope::class)
    class PixelInterceptorPluginModuleExt {
        @Provides
        @SingleInstanceIn(AppScope::class)
        fun providePixelInterceptorPlugins(
            plugins: Set<@JvmSuppressWildcards PixelInterceptorPlugin>
        ): PluginPoint<PixelInterceptorPlugin> {
            return PixelInterceptorPluginPoint(plugins)
        }
    }
}
