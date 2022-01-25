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

package com.duckduckgo.app.statistics.api

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.multibindings.Multibinds

interface RefreshRetentionAtbPlugin {
    /**
     * Will be called right after we have refreshed the ATB retention on search
     */
    fun onSearchRetentionAtbRefreshed()

    /**
     * Will be called right after we have refreshed the ATB retention on search
     */
    fun onAppRetentionAtbRefreshed()
}

class RefreshRetentionAtbPluginPoint(
    private val plugins: DaggerSet<RefreshRetentionAtbPlugin>
) : PluginPoint<RefreshRetentionAtbPlugin> {
    override fun getPlugins(): Collection<RefreshRetentionAtbPlugin> {
        return plugins.sortedBy { it.javaClass.simpleName }
    }
}

@Module
@ContributesTo(AppScope::class)
abstract class RefreshRetentionAtbPluginModule {
    @Multibinds
    abstract fun bindRefreshRetentionAtbPlugins(): DaggerSet<RefreshRetentionAtbPlugin>
}
