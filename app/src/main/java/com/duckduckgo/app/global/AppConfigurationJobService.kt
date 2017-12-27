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

package com.duckduckgo.app.global

import android.app.job.JobParameters
import android.app.job.JobService
import com.duckduckgo.app.httpsupgrade.api.HttpsUpgradeListDownloader
import com.duckduckgo.app.trackerdetection.Client.ClientName.*
import com.duckduckgo.app.trackerdetection.api.TrackerDataDownloader
import dagger.android.AndroidInjection
import io.reactivex.Completable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject


class AppConfigurationJobService : JobService() {

    @Inject
    lateinit var trackerDataDownloader: TrackerDataDownloader

    @Inject
    lateinit var httpsUpgradeListDownloader: HttpsUpgradeListDownloader

    private var downloadTask: Disposable? = null

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Timber.i("onStartJob")

        val easyListDownload = trackerDataDownloader.downloadList(EASYLIST)
        val easyPrivacyDownload = trackerDataDownloader.downloadList(EASYPRIVACY)
        val disconnectDownload = trackerDataDownloader.downloadList(DISCONNECT)
        val httpsUpgradeDownload = httpsUpgradeListDownloader.downloadList()

        Completable.merge(mutableListOf(easyListDownload, easyPrivacyDownload, disconnectDownload, httpsUpgradeDownload))
                .subscribeOn(Schedulers.io())
                .doOnComplete {
                    Timber.i("Successfully downloaded all data")
                    jobFinishedSuccessfully(params)
                }
                .doOnError({
                    Timber.w("Failed to download all data")
                    jobFinishedFailed(params)
                })
                .subscribeOn(Schedulers.io())
                .subscribe()

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