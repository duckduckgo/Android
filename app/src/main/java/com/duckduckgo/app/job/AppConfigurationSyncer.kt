/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.job

import androidx.annotation.CheckResult
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.duckduckgo.app.global.job.AppConfigurationSyncWorkRequestBuilder
import com.duckduckgo.app.global.job.AppConfigurationSyncWorkRequestBuilder.Companion.APP_CONFIG_SYNC_WORK_TAG
import io.reactivex.Completable
import timber.log.Timber

class AppConfigurationSyncer(
    private val appConfigurationSyncWorkRequestBuilder: AppConfigurationSyncWorkRequestBuilder,
    private val workManager: WorkManager,
    private val appConfigurationDownloader: ConfigurationDownloader
) {

    @CheckResult
    fun scheduleImmediateSync(): Completable {
        Timber.i("Running immediate attempt to download app configuration")
        return appConfigurationDownloader.downloadTask()
    }

    fun scheduleRegularSync() {
        Timber.i("Scheduling regular sync")
        val workRequest = appConfigurationSyncWorkRequestBuilder.appConfigurationWork()
        workManager.enqueueUniquePeriodicWork(APP_CONFIG_SYNC_WORK_TAG, ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }
}
