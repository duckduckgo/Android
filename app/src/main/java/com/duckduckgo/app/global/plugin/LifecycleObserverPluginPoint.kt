/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.global.plugin

import androidx.lifecycle.LifecycleObserver
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.multibindings.Multibinds
import javax.inject.Inject
import dagger.SingleInstanceIn

@Module
@ContributesTo(AppScope::class)
abstract class LifecycleObserverPluginProviderModule {
    // we use multibinds as the list of plugins can be empty
    @Multibinds
    abstract fun provideLifecycleObserverPlugins(): DaggerSet<LifecycleObserver>

    @Binds
    @SingleInstanceIn(AppScope::class)
    abstract fun provideLifecycleObserverPluginProvider(
        lifecycleObserverPluginPoint: LifecycleObserverPluginPoint
    ): PluginPoint<LifecycleObserver>
}

@SingleInstanceIn(AppScope::class)
class LifecycleObserverPluginPoint @Inject constructor(
    private val plugins: DaggerSet<LifecycleObserver>
) : PluginPoint<LifecycleObserver> {
    override fun getPlugins(): Set<LifecycleObserver> {
        return plugins
    }
}
