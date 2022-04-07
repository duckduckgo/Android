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

package com.duckduckgo.app.notification

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.notification.model.SchedulableNotificationPlugin
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import dagger.multibindings.Multibinds

class SchedulableNotificationPluginPoint(
    private val schedulableNotificationPlugins: DaggerSet<SchedulableNotificationPlugin>
) : PluginPoint<SchedulableNotificationPlugin> {
    override fun getPlugins(): Collection<SchedulableNotificationPlugin> {
        return schedulableNotificationPlugins
    }
}

@Module
@ContributesTo(AppScope::class)
abstract class SchedulableNotificationPluginModule {
    @Multibinds
    abstract fun bindEmptySchedulableNotificationPlugins(): DaggerSet<SchedulableNotificationPlugin>

    @Module
    @ContributesTo(AppScope::class)
    class SchedulableNotificationPluginModuleExt {
        @Provides
        @SingleInstanceIn(AppScope::class)
        fun provideSchedulableNotificationPluginPoint(
            plugins: DaggerSet<SchedulableNotificationPlugin>
        ): PluginPoint<SchedulableNotificationPlugin> {
            return SchedulableNotificationPluginPoint(plugins)
        }
    }
}
