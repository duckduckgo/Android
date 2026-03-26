/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl

import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.duckduckgo.remote.messaging.store.RemoteMessagingConfigRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.VERBOSE
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesMultibinding(
    AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
class RemoteMessagingPrivacyConfigObserver @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val remoteMessagingConfigRepository: RemoteMessagingConfigRepository,
    private val remoteMessagingFeatureToggles: RemoteMessagingFeatureToggles,
    private val workManager: WorkManager,
) : PrivacyConfigCallbackPlugin {

    override fun onPrivacyConfigDownloaded() {
        appCoroutineScope.launch(context = dispatcherProvider.io()) {
            val invalidateConfig = remoteMessagingFeatureToggles.invalidateRMFAfterPrivacyConfigDownloaded().isEnabled()
            val alwaysProcess = remoteMessagingFeatureToggles.alwaysProcessRemoteConfig().isEnabled()
            // we only need to invalidate, so we process config next run, if alwaysProcess is not enabled
            val shouldInvalidate = invalidateConfig && alwaysProcess.not()

            if (shouldInvalidate) {
                logcat { "RMF: onPrivacyConfigDownloaded, invalidate so next run we process the config" }
                remoteMessagingConfigRepository.invalidate()
            }
            scheduleDownload()
        }
    }

    private fun scheduleDownload() {
        if (remoteMessagingFeatureToggles.canScheduleOnPrivacyConfigUpdates().isEnabled()) {
            val refreshInterval = if (remoteMessagingFeatureToggles.scheduleEveryHour().isEnabled()) 1L else 4L

            logcat(VERBOSE) { "RMF: Scheduling remote config worker with fresh interval of $refreshInterval hours" }
            val workerRequest = PeriodicWorkRequestBuilder<RemoteMessagingConfigDownloadWorker>(refreshInterval, TimeUnit.HOURS)
                .addTag(REMOTE_MESSAGING_DOWNLOADER_WORKER_TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(
                REMOTE_MESSAGING_DOWNLOADER_WORKER_TAG,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                workerRequest,
            )
        }
    }
}
