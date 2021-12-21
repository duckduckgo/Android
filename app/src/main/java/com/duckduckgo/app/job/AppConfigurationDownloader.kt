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

import com.duckduckgo.app.httpsupgrade.api.HttpsUpgradeDataDownloader
import com.duckduckgo.app.surrogates.api.ResourceSurrogateListDownloader
import com.duckduckgo.app.survey.api.SurveyDownloader
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
    private val surveyDownloader: SurveyDownloader
) : ConfigurationDownloader {

    override fun downloadTask(): Completable {
        val tdsDownload = trackerDataDownloader.downloadTds()
        val clearLegacyLists = trackerDataDownloader.clearLegacyLists()
        val surrogatesDownload = resourceSurrogateDownloader.downloadList()
        val httpsUpgradeDownload = httpsUpgradeDataDownloader.download()
        val surveyDownload = surveyDownloader.download()

        return Completable.mergeDelayError(
                listOf(
                    tdsDownload,
                    clearLegacyLists,
                    surrogatesDownload,
                    httpsUpgradeDownload,
                    surveyDownload))
            .doOnComplete { Timber.i("Download task completed successfully") }
    }
}
