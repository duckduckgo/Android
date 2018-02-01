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

import com.duckduckgo.app.surrogates.api.ResourceSurrogateListDownloader
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.httpsupgrade.api.HttpsUpgradeListDownloader
import com.duckduckgo.app.settings.db.AppConfigurationEntity
import com.duckduckgo.app.trackerdetection.Client.ClientName.*
import com.duckduckgo.app.trackerdetection.api.TrackerDataDownloader
import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

class AppConfigurationDownloader @Inject constructor(
        private val trackerDataDownloader: TrackerDataDownloader,
        private val httpsUpgradeListDownloader: HttpsUpgradeListDownloader,
        private val resourceSurrogateDownloader: ResourceSurrogateListDownloader,
        private val appDatabase: AppDatabase) {

    fun downloadTask(): Completable {
        val easyListDownload = trackerDataDownloader.downloadList(EASYLIST)
        val easyPrivacyDownload = trackerDataDownloader.downloadList(EASYPRIVACY)
        val trackersWhitelist = trackerDataDownloader.downloadList(TRACKERSWHITELIST)
        val disconnectDownload = trackerDataDownloader.downloadList(DISCONNECT)
        val surrogatesDownload = resourceSurrogateDownloader.downloadList()
        val httpsUpgradeDownload = httpsUpgradeListDownloader.downloadList()

        return Completable.mergeDelayError(listOf(
                easyListDownload.subscribeOn(Schedulers.io()),
                easyPrivacyDownload.subscribeOn(Schedulers.io()),
                trackersWhitelist.subscribeOn(Schedulers.io()),
                disconnectDownload.subscribeOn(Schedulers.io()),
                surrogatesDownload.subscribeOn(Schedulers.io()),
                httpsUpgradeDownload.subscribeOn(Schedulers.io())
        )).doOnComplete {
            Timber.i("Download task completed successfully")
            val appConfiguration = AppConfigurationEntity(appConfigurationDownloaded = true)
            appDatabase.appConfigurationDao().configurationDownloadSuccessful(appConfiguration)
        }
    }
}