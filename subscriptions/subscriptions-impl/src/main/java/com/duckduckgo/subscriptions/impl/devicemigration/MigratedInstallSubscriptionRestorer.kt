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

package com.duckduckgo.subscriptions.impl.devicemigration

import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.RealSubscriptionsManager.RecoverSubscriptionResult
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.logcat
import javax.inject.Inject

/**
 * The subscription's keystore-encrypted store is not transferred, so after a device transfer the user looks signed out even though
 * a Play purchase is still tied to their Google account. This restores it silently, once.
 * The attempt is recorded before the call so a failing or hanging restore is never retried on every start.
 * The guard preference file is excluded from transfer, otherwise a chained transfer would skip the restore.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<MainProcessLifecycleObserver>())
class MigratedInstallSubscriptionRestorer @Inject constructor(
    private val deviceMigration: DeviceMigration,
    private val subscriptionsManager: SubscriptionsManager,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver {

    private val preferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(PREFERENCES_FILE_NAME, false, false)
    }

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) {
            if (preferences.getBoolean(KEY_RESTORE_ATTEMPTED, false)) return@launch
            if (!deviceMigration.isMigratedInstall()) return@launch

            preferences.edit(commit = true) { putBoolean(KEY_RESTORE_ATTEMPTED, true) }

            try {
                if (subscriptionsManager.isSignedIn()) return@launch
                when (subscriptionsManager.recoverSubscriptionFromStore()) {
                    is RecoverSubscriptionResult.Success -> logcat(INFO) { "Subscription restored after device transfer" }
                    is RecoverSubscriptionResult.Failure -> logcat(INFO) { "No subscription restored after device transfer" }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(ERROR) { "Subscription restore after device transfer failed: ${e.message}" }
            }
        }
    }

    private companion object {
        const val PREFERENCES_FILE_NAME = "com.duckduckgo.subscriptions.devicemigration"
        const val KEY_RESTORE_ATTEMPTED = "restoreAttempted"
    }
}
