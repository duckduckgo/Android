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

import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.plugins.migrations.MigrationPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.Gpc
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import logcat.logcat

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class GpcMigrationPlugin @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val gpc: Gpc,
) : MigrationPlugin {

    override val version: Int = 1

    override fun run() {
        logcat { "Migrating gpc settings" }
        val gpcEnabled = settingsDataStore.globalPrivacyControlEnabled
        if (gpcEnabled) {
            gpc.enableGpc()
        } else {
            gpc.disableGpc()
        }
    }
}
