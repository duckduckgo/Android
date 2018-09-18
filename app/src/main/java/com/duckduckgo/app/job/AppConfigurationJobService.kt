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

import android.app.job.JobParameters
import android.app.job.JobService
import dagger.android.AndroidInjection
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject


class AppConfigurationJobService : JobService() {

    @Inject
    lateinit var appConfigurationDownloader: ConfigurationDownloader

    private var downloadTask: Disposable? = null

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Timber.i("onStartJob")

        downloadTask = appConfigurationDownloader.downloadTask()
            .subscribeOn(Schedulers.io())
            .subscribe({
                Timber.i("Successfully downloaded all data")
                jobFinishedSuccessfully(params)
            }, {
                Timber.w("Failed to download app configuration ${it.localizedMessage}")
                jobFinishedFailed(params)
            })

        return true
    }

    private fun jobFinishedSuccessfully(params: JobParameters?) {
        Timber.i("Finished job successfully")
        jobFinished(params, false)
    }

    private fun jobFinishedFailed(params: JobParameters?) {
        Timber.w("Error executing job")
        jobFinished(params, true)
    }

    override fun onStopJob(p0: JobParameters?): Boolean {
        Timber.i("onStopJob")

        // job needs to be force stopped
        downloadTask?.dispose()

        return true
    }
}