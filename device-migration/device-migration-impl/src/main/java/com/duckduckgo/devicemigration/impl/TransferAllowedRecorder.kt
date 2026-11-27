/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.devicemigration.impl

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.devicemigration.impl.DeviceMigrationMarkerStore.Companion.KEY_TRANSFER_ALLOWED
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Saves whether this phone may hand its data to a new phone, for [DeviceMigrationBackupAgent] to read: the agent runs in
 * restricted mode and cannot evaluate the remote flag itself. Refreshed after each config download as well as on every start,
 * so a remote "off" reaches the agent without waiting for the next start.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<MainProcessLifecycleObserver>())
@ContributesIntoSet(AppScope::class, binding = binding<PrivacyConfigCallbackPlugin>())
class TransferAllowedRecorder @Inject constructor(
    private val markerStore: DeviceMigrationMarkerStore,
    private val deviceMigrationFeature: DeviceMigrationFeature,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver, PrivacyConfigCallbackPlugin {

    override fun onCreate(owner: LifecycleOwner) = record()

    override fun onPrivacyConfigDownloaded() = record()

    private fun record() {
        appCoroutineScope.launch(dispatchers.io()) {
            val allowed = deviceMigrationFeature.self().isEnabled()
            markerStore.local.edit().putBoolean(KEY_TRANSFER_ALLOWED, allowed).commit()
        }
    }
}
