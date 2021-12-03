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
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds
import javax.inject.Singleton

class UserAgentOverridePluginPoint(
    private val userAgentOverride: Set<@JvmSuppressWildcards UserAgentOverride>
) : PluginPoint<UserAgentOverride> {
    override fun getPlugins(): Collection<UserAgentOverride> {
        return userAgentOverride
    }
}

@Module
@ContributesTo(AppObjectGraph::class)
abstract class UserAgentOverrideBindingModule {

    @Multibinds
    abstract fun provideUserAgentOverridePlugins(): Set<@JvmSuppressWildcards UserAgentOverride>
}

@Module
@ContributesTo(AppObjectGraph::class)
class UserAgentPluginPointModule {

    @Provides
    @Singleton
    fun provideUserAgentOverridePluginPoint(userAgentOverride: Set<@JvmSuppressWildcards UserAgentOverride>): PluginPoint<UserAgentOverride> {
        return UserAgentOverridePluginPoint(userAgentOverride)
    }
}
