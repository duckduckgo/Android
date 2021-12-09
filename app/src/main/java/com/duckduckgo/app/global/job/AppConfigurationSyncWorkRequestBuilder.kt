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

package com.duckduckgo.app.global.job

import android.content.Context
import androidx.work.*
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.app.job.ConfigurationDownloader
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import io.reactivex.Single
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class AppConfigurationSyncWorkRequestBuilder @Inject constructor() {

    fun appConfigurationWork(): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<AppConfigurationWorker>(12, TimeUnit.HOURS)
            .setConstraints(networkAvailable())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.MINUTES)
            .addTag(APP_CONFIG_SYNC_WORK_TAG)
            .build()
    }

    private fun networkAvailable() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    companion object {
        const val APP_CONFIG_SYNC_WORK_TAG = "AppConfigurationWorker"
    }
}

class AppConfigurationWorker(context: Context, workerParams: WorkerParameters) : RxWorker(context, workerParams) {

    @Inject
    lateinit var appConfigurationDownloader: ConfigurationDownloader

    override fun createWork(): Single<Result> {
        Timber.i("Running app config sync")
        return appConfigurationDownloader.downloadTask()
            .toSingle {
                Timber.i("App configuration sync was successful")
                Result.success()
            }
            .onErrorReturn {
                Timber.w(it, "App configuration sync work failed")
                Result.retry()
            }
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class AppConfigurationWorkerInjectorPlugin @Inject constructor(
    private val configurationDownloader: ConfigurationDownloader
) : WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is AppConfigurationWorker) {
            worker.appConfigurationDownloader = configurationDownloader
            return true
        }
        return false
    }
}
