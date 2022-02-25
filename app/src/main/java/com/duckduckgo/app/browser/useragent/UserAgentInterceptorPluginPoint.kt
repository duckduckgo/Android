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

package com.duckduckgo.app.browser.useragent

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import dagger.multibindings.Multibinds

class UserAgentInterceptorPluginPoint(
    private val userAgentInterceptor: Set<@JvmSuppressWildcards UserAgentInterceptor>
) : PluginPoint<UserAgentInterceptor> {
    override fun getPlugins(): Collection<UserAgentInterceptor> {
        return userAgentInterceptor
    }
}

@Module
@ContributesTo(AppScope::class)
abstract class UserAgentInterceptorBindingModule {

    @Multibinds
    abstract fun provideUserAgentInterceptorPlugins(): Set<@JvmSuppressWildcards UserAgentInterceptor>
}

@Module
@ContributesTo(AppScope::class)
class UserAgentPluginPointModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideUserAgentInterceptorPluginPoint(
        userAgentInterceptor: Set<@JvmSuppressWildcards UserAgentInterceptor>
    ): PluginPoint<UserAgentInterceptor> {
        return UserAgentInterceptorPluginPoint(userAgentInterceptor)
    }
}
