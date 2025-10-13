/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.notification.checkPermissionAndNotify
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.duckduckgo.sync.impl.engine.SyncNotificationBuilder
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface SyncFeatureToggle {
    fun showSync(): Boolean

    fun allowDataSyncing(): Boolean

    fun allowDataSyncingOnNewerVersion(): Boolean

    fun allowSetupFlows(): Boolean

    fun allowSetupFlowsOnNewerVersion(): Boolean

    fun allowCreateAccount(): Boolean

    fun allowCreateAccountOnNewerVersion(): Boolean

    fun automaticallyUpdateSyncSettings(): Boolean
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = SyncFeatureToggle::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
@SingleInstanceIn(AppScope::class)
class SyncRemoteFeatureToggle @Inject constructor(
    private val context: Context,
    private val syncFeature: SyncFeature,
    private val appBuildConfig: AppBuildConfig,
    private val notificationManager: NotificationManagerCompat,
    private val syncNotificationBuilder: SyncNotificationBuilder,
    private val syncStore: SyncStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val coroutineDispatcher: DispatcherProvider = DefaultDispatcherProvider(),
) : SyncFeatureToggle, PrivacyConfigCallbackPlugin {

    private fun isFeatureEnabled(): Boolean {
        return syncFeature.self().isEnabled()
    }

    override fun showSync(): Boolean {
        if (appBuildConfig.isInternalBuild()) return syncFeature.level0ShowSync().isEnabled()
        return isFeatureEnabled() && syncFeature.level0ShowSync().isEnabled()
    }

    override fun allowDataSyncing(): Boolean {
        if (!showSync()) return false
        return syncFeature.level1AllowDataSyncing().isEnabled()
    }

    override fun allowDataSyncingOnNewerVersion(): Boolean {
        return isToggleEnabledOnNewerVersion(syncFeature.level1AllowDataSyncing())
    }

    override fun allowSetupFlows(): Boolean {
        if (!showSync()) return false
        if (!syncFeature.level1AllowDataSyncing().isEnabled()) return false
        return syncFeature.level2AllowSetupFlows().isEnabled()
    }

    override fun allowSetupFlowsOnNewerVersion(): Boolean {
        return isToggleEnabledOnNewerVersion(syncFeature.level2AllowSetupFlows())
    }

    override fun allowCreateAccount(): Boolean {
        if (!showSync()) return false
        if (!syncFeature.level1AllowDataSyncing().isEnabled()) return false
        if (!syncFeature.level2AllowSetupFlows().isEnabled()) return false
        return syncFeature.level3AllowCreateAccount().isEnabled()
    }

    override fun allowCreateAccountOnNewerVersion(): Boolean {
        return isToggleEnabledOnNewerVersion(syncFeature.level3AllowCreateAccount())
    }

    override fun automaticallyUpdateSyncSettings(): Boolean {
        return syncFeature.automaticallyUpdateSyncSettings().isEnabled()
    }

    private fun isToggleEnabledOnNewerVersion(toggle: Toggle): Boolean {
        val rawStoredState = toggle.getRawStoredState()

        return rawStoredState?.remoteEnableState == true &&
            appBuildConfig.versionCode < (rawStoredState.minSupportedVersion ?: 0)
    }

    override fun onPrivacyConfigDownloaded() {
        appCoroutineScope.launch(coroutineDispatcher.io()) {
            val canSyncData = allowDataSyncing()

            if (!canSyncData && syncStore.syncingDataEnabled && syncStore.isSignedIn()) {
                triggerNotification()
            }

            if (canSyncData && !syncStore.syncingDataEnabled) {
                cancelNotification()
            }

            syncStore.syncingDataEnabled = canSyncData
        }
    }

    private fun triggerNotification() {
        val showSync = showSync()
        notificationManager.checkPermissionAndNotify(
            context,
            SYNC_PAUSED_NOTIFICATION_ID,
            syncNotificationBuilder.buildSyncPausedNotification(context, addNavigationIntent = showSync),
        )
    }
    private fun cancelNotification() {
        notificationManager.cancel(SYNC_PAUSED_NOTIFICATION_ID)
    }

    companion object {
        private const val SYNC_PAUSED_NOTIFICATION_ID = 6451
    }
}
