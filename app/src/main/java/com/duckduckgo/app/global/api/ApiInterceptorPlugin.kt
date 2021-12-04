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
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds
import okhttp3.Interceptor
import dagger.SingleInstanceIn

interface ApiInterceptorPlugin {
    fun getInterceptor(): Interceptor
}

private class ApiInterceptorPluginPoint(
    private val plugins: DaggerSet<ApiInterceptorPlugin>
) : PluginPoint<ApiInterceptorPlugin> {
    override fun getPlugins(): Collection<ApiInterceptorPlugin> {
        return plugins
    }
}

@Module
@ContributesTo(AppScope::class)
abstract class ApiInterceptorPluginModule {
    @Multibinds
    abstract fun bindEmptyApiInterceptorPlugins(): DaggerSet<ApiInterceptorPlugin>

    @Module
    @ContributesTo(AppScope::class)
    class ApiInterceptorPluginModuleExt {
        @Provides
        @SingleInstanceIn(AppScope::class)
        fun provideApiInterceptorPlugins(
            plugins: DaggerSet<ApiInterceptorPlugin>
        ): PluginPoint<ApiInterceptorPlugin> {
            return ApiInterceptorPluginPoint(plugins)
        }
    }
}
