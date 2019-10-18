/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.di

import com.duckduckgo.app.entities.api.EntityListDownloader
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.httpsupgrade.api.HttpsUpgradeDataDownloader
import com.duckduckgo.app.job.AppConfigurationDownloader
import com.duckduckgo.app.job.ConfigurationDownloader
import com.duckduckgo.app.surrogates.api.ResourceSurrogateListDownloader
import com.duckduckgo.app.survey.api.SurveyDownloader
import com.duckduckgo.app.trackerdetection.api.TrackerDataDownloader
import dagger.Module
import dagger.Provides

@Module
open class AppConfigurationDownloaderModule {

    @Provides
    open fun appConfigurationDownloader(
        trackerDataDownloader: TrackerDataDownloader,
        httpsUpgradeDataDownloader: HttpsUpgradeDataDownloader,
        resourceSurrogateDownloader: ResourceSurrogateListDownloader,
        entityListDownloader: EntityListDownloader,
        surveyDownloader: SurveyDownloader,
        appDatabase: AppDatabase
    ): ConfigurationDownloader {

        return AppConfigurationDownloader(
            trackerDataDownloader,
            httpsUpgradeDataDownloader,
            resourceSurrogateDownloader,
            entityListDownloader,
            surveyDownloader,
            appDatabase
        )
    }
}