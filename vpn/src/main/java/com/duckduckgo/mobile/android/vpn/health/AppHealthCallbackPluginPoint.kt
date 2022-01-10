/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.health

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import dagger.multibindings.Multibinds

class AppHealthCallbackPluginPoint(
    private val plugins: DaggerSet<AppHealthCallback>
) : PluginPoint<AppHealthCallback> {
    override fun getPlugins(): Collection<AppHealthCallback> {
        return plugins.sortedBy { it.javaClass.simpleName }
    }
}

@Module
@ContributesTo(AppScope::class)
abstract class AppHealthCallbackModule {

    @Multibinds
    abstract fun AppHealthCallbackPluginPoint.bind(): DaggerSet<AppHealthCallback>

    companion object {
        @Provides
        @SingleInstanceIn(AppScope::class)
        fun provideAppHealthCallbackPluginPoint(callbacks: DaggerSet<AppHealthCallback>) : PluginPoint<AppHealthCallback> {
            return AppHealthCallbackPluginPoint(callbacks)
        }
    }
}
