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

package com.duckduckgo.app.global.migrations

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.global.plugins.migrations.MigrationPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import dagger.SingleInstanceIn

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class MigrationLifecycleObserver @Inject constructor(
    private val migrationPluginPoint: PluginPoint<MigrationPlugin>,
    private val migrationStore: MigrationStore
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun migrate() {
        val currentVersion = migrationStore.version
        migrationPluginPoint.getPlugins()
            .sortedBy { it.version }
            .filter {
                currentVersion < it.version
            }.forEach {
                it.run()
            }

        if (currentVersion < CURRENT_VERSION) {
            migrationStore.version = CURRENT_VERSION
        }
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}
