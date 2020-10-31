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

package com.duckduckgo.app.di

import android.content.Context
import androidx.work.WorkManager
import com.duckduckgo.app.autocomplete.api.AutoCompleteService
import com.duckduckgo.app.brokensite.api.BrokenSiteSender
import com.duckduckgo.app.brokensite.api.BrokenSiteSubmitter
import com.duckduckgo.app.feedback.api.FeedbackService
import com.duckduckgo.app.feedback.api.FeedbackSubmitter
import com.duckduckgo.app.feedback.api.FireAndForgetFeedbackSubmitter
import com.duckduckgo.app.feedback.api.SubReasonApiMapper
import com.duckduckgo.app.global.AppUrl.Url
import com.duckduckgo.app.global.api.ApiRequestInterceptor
import com.duckduckgo.app.global.api.NetworkApiCache
import com.duckduckgo.app.global.job.AppConfigurationSyncWorkRequestBuilder
import com.duckduckgo.app.httpsupgrade.api.HttpsUpgradeService
import com.duckduckgo.app.job.AppConfigurationSyncer
import com.duckduckgo.app.job.ConfigurationDownloader
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.surrogates.api.ResourceSurrogateListService
import com.duckduckgo.app.survey.api.SurveyService
import com.duckduckgo.app.trackerdetection.api.TrackerListService
import com.duckduckgo.app.trackerdetection.db.TdsMetadataDao
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
class NetworkModule {

    @Provides
    @Singleton
    @Named("api")
    fun apiOkHttpClient(context: Context, apiRequestInterceptor: ApiRequestInterceptor): OkHttpClient {
        val cacheLocation = File(context.cacheDir, NetworkApiCache.FILE_NAME)
        val cache = Cache(cacheLocation, CACHE_SIZE)
        return OkHttpClient.Builder()
            .addInterceptor(apiRequestInterceptor)
            .addInterceptor(LoggingInterceptor())
            .cache(cache)
            .build()
    }

    @Provides
    @Singleton
    @Named("nonCaching")
    fun pixelOkHttpClient(apiRequestInterceptor: ApiRequestInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(apiRequestInterceptor)
            .addInterceptor(LoggingInterceptor())
            .build()
    }

    @Provides
    @Singleton
    @Named("api")
    fun apiRetrofit(@Named("api") okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Url.API)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    @Named("nonCaching")
    fun nonCachingRetrofit(@Named("nonCaching") okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Url.API)
            .client(okHttpClient)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    fun apiRequestInterceptor(context: Context): ApiRequestInterceptor {
        return ApiRequestInterceptor(context)
    }

    @Provides
    fun trackerListService(@Named("api") retrofit: Retrofit): TrackerListService =
        retrofit.create(TrackerListService::class.java)

    @Provides
    fun httpsUpgradeService(@Named("api") retrofit: Retrofit): HttpsUpgradeService =
        retrofit.create(HttpsUpgradeService::class.java)

    @Provides
    fun autoCompleteService(@Named("nonCaching") retrofit: Retrofit): AutoCompleteService =
        retrofit.create(AutoCompleteService::class.java)

    @Provides
    fun surrogatesService(@Named("api") retrofit: Retrofit): ResourceSurrogateListService =
        retrofit.create(ResourceSurrogateListService::class.java)

    @Provides
    fun brokenSiteSender(
        statisticsStore: StatisticsDataStore,
        variantManager: VariantManager,
        tdsMetadataDao: TdsMetadataDao,
        pixel: Pixel
    ): BrokenSiteSender =
        BrokenSiteSubmitter(statisticsStore, variantManager, tdsMetadataDao, pixel)

    @Provides
    fun surveyService(@Named("api") retrofit: Retrofit): SurveyService =
        retrofit.create(SurveyService::class.java)

    @Provides
    fun feedbackSubmitter(
        feedbackService: FeedbackService,
        variantManager: VariantManager,
        apiKeyMapper: SubReasonApiMapper,
        statisticsStore: StatisticsDataStore,
        pixel: Pixel
    ): FeedbackSubmitter =
        FireAndForgetFeedbackSubmitter(feedbackService, variantManager, apiKeyMapper, statisticsStore, pixel)

    @Provides
    fun feedbackService(@Named("api") retrofit: Retrofit): FeedbackService =
        retrofit.create(FeedbackService::class.java)

    @Provides
    @Singleton
    fun appConfigurationSyncer(
        appConfigurationSyncWorkRequestBuilder: AppConfigurationSyncWorkRequestBuilder,
        workManager: WorkManager,
        appConfigurationDownloader: ConfigurationDownloader
    ): AppConfigurationSyncer {
        return AppConfigurationSyncer(appConfigurationSyncWorkRequestBuilder, workManager, appConfigurationDownloader)
    }

    companion object {
        private const val CACHE_SIZE: Long = 10 * 1024 * 1024 // 10MB
    }
}
