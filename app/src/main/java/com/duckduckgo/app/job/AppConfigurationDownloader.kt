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

import com.duckduckgo.app.entities.api.EntityListDownloader
import com.duckduckgo.app.global.db.AppConfigurationEntity
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.httpsupgrade.api.HttpsUpgradeDataDownloader
import com.duckduckgo.app.surrogates.api.ResourceSurrogateListDownloader
import com.duckduckgo.app.survey.api.SurveyDownloader
import com.duckduckgo.app.trackerdetection.Client.ClientName.*
import com.duckduckgo.app.trackerdetection.api.TrackerDataDownloader
import io.reactivex.Completable
import timber.log.Timber

interface ConfigurationDownloader {
    fun downloadTask(): Completable
}

class AppConfigurationDownloader(
    private val trackerDataDownloader: TrackerDataDownloader,
    private val httpsUpgradeDataDownloader: HttpsUpgradeDataDownloader,
    private val resourceSurrogateDownloader: ResourceSurrogateListDownloader,
    private val entityListDownloader: EntityListDownloader,
    private val surveyDownloader: SurveyDownloader,
    private val appDatabase: AppDatabase
) : ConfigurationDownloader {

    override fun downloadTask(): Completable {
        val easyListDownload = trackerDataDownloader.downloadList(EASYLIST)
        val easyPrivacyDownload = trackerDataDownloader.downloadList(EASYPRIVACY)
        val trackersWhitelist = trackerDataDownloader.downloadList(TRACKERSWHITELIST)
        val disconnectDownload = trackerDataDownloader.downloadList(DISCONNECT)
        val entityListDownload = entityListDownloader.download()
        val surrogatesDownload = resourceSurrogateDownloader.downloadList()
        val httpsUpgradeDownload = httpsUpgradeDataDownloader.download()
        val surveyDownload = surveyDownloader.download()

        return Completable.mergeDelayError(
            listOf(
                easyListDownload,
                easyPrivacyDownload,
                trackersWhitelist,
                disconnectDownload,
                entityListDownload,
                surrogatesDownload,
                httpsUpgradeDownload,
                surveyDownload
            )
        ).doOnComplete {
            Timber.i("Download task completed successfully")
            val appConfiguration = AppConfigurationEntity(appConfigurationDownloaded = true)
            appDatabase.appConfigurationDao().configurationDownloadSuccessful(appConfiguration)
        }
    }
}