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

import android.annotation.SuppressLint
import androidx.annotation.CheckResult
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.duckduckgo.app.global.job.AppConfigurationSyncWorkRequestBuilder
import com.duckduckgo.app.global.job.AppConfigurationSyncWorkRequestBuilder.Companion.APP_CONFIG_SYNC_WORK_TAG
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import dagger.multibindings.IntoSet
import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers
import logcat.LogPriority.INFO
import logcat.LogPriority.WARN
import logcat.logcat

@Module
@ContributesTo(AppScope::class)
class AppConfigurationSyncerModule {
    @Provides
    @SingleInstanceIn(AppScope::class)
    @IntoSet
    fun provideAppConfigurationSyncer(
        appConfigurationSyncWorkRequestBuilder: AppConfigurationSyncWorkRequestBuilder,
        workManager: WorkManager,
        appConfigurationDownloader: ConfigurationDownloader,
    ): MainProcessLifecycleObserver {
        return AppConfigurationSyncer(appConfigurationSyncWorkRequestBuilder, workManager, appConfigurationDownloader)
    }
}

@VisibleForTesting
class AppConfigurationSyncer(
    private val appConfigurationSyncWorkRequestBuilder: AppConfigurationSyncWorkRequestBuilder,
    private val workManager: WorkManager,
    private val appConfigurationDownloader: ConfigurationDownloader,
) : MainProcessLifecycleObserver {

    @SuppressLint("CheckResult")
    @UiThread
    override fun onCreate(owner: LifecycleOwner) {
        scheduleImmediateSync()
            .subscribeOn(Schedulers.io())
            .doAfterTerminate {
                scheduleRegularSync()
            }
            .subscribe({}, { logcat(WARN) { "Failed to download initial app configuration ${it.localizedMessage}" } })
    }

    @CheckResult
    fun scheduleImmediateSync(): Completable {
        logcat(INFO) { "Running immediate attempt to download app configuration" }
        return appConfigurationDownloader.downloadTask()
    }

    fun scheduleRegularSync() {
        logcat(INFO) { "Scheduling regular sync" }
        val workRequest = appConfigurationSyncWorkRequestBuilder.appConfigurationWork()
        workManager.enqueueUniquePeriodicWork(APP_CONFIG_SYNC_WORK_TAG, ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }
}
