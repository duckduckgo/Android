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
import com.duckduckgo.app.autocomplete.api.AutoCompleteService
import com.duckduckgo.app.brokensite.api.BrokenSiteSender
import com.duckduckgo.app.brokensite.api.BrokenSiteSubmitter
import com.duckduckgo.app.browser.useragent.UserAgentProvider
import com.duckduckgo.app.email.api.EmailService
import com.duckduckgo.app.feedback.api.FeedbackService
import com.duckduckgo.app.feedback.api.FeedbackSubmitter
import com.duckduckgo.app.feedback.api.FireAndForgetFeedbackSubmitter
import com.duckduckgo.app.feedback.api.SubReasonApiMapper
import com.duckduckgo.app.global.AppUrl.Url
import com.duckduckgo.app.global.api.*
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.global.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.app.httpsupgrade.api.HttpsUpgradeService
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.surrogates.api.ResourceSurrogateListService
import com.duckduckgo.app.survey.api.SurveyService
import com.duckduckgo.app.trackerdetection.api.TrackerListService
import com.duckduckgo.app.trackerdetection.db.TdsMetadataDao
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.mobile.android.vpn.waitlist.api.AppTrackingProtectionWaitlistService
import com.duckduckgo.privacy.config.api.Gpc
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import java.io.File
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import timber.log.Timber

@Module
class NetworkModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    @Named("api")
    fun apiOkHttpClient(
        context: Context,
        apiRequestInterceptor: ApiRequestInterceptor,
        apiInterceptorPlugins: PluginPoint<ApiInterceptorPlugin>
    ): OkHttpClient {
        val cacheLocation = File(context.cacheDir, NetworkApiCache.FILE_NAME)
        val cache = Cache(cacheLocation, CACHE_SIZE)
        return OkHttpClient.Builder()
            .addInterceptor(apiRequestInterceptor)
            .cache(cache)
            .apply {
                apiInterceptorPlugins.getPlugins().forEach { addInterceptor(it.getInterceptor()) }
            }
            .build()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    @Named("nonCaching")
    fun pixelOkHttpClient(
        apiRequestInterceptor: ApiRequestInterceptor,
        pixelReQueryInterceptor: PixelReQueryInterceptor,
        pixelEmailRemovalInterceptor: PixelEmailRemovalInterceptor,
        pixelInterceptorPlugins: PluginPoint<PixelInterceptorPlugin>,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(apiRequestInterceptor)
            .addInterceptor(pixelReQueryInterceptor)
            .addInterceptor(pixelEmailRemovalInterceptor)
            .apply {
                pixelInterceptorPlugins.getPlugins().forEach { addInterceptor(it.getInterceptor()) }
            }
            // shall be the last one as it is logging the pixel request url that goes out
            .addInterceptor { chain: Interceptor.Chain ->
                Timber.v("Pixel url request: ${chain.request().url}")
                return@addInterceptor chain.proceed(chain.request())
            }
            .build()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    @Named("api")
    fun apiRetrofit(@Named("api") okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Url.API)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    @Named("nonCaching")
    fun nonCachingRetrofit(
        @Named("nonCaching") okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Url.API)
            .client(okHttpClient)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    fun apiRequestInterceptor(
        context: Context,
        userAgentProvider: UserAgentProvider
    ): ApiRequestInterceptor {
        return ApiRequestInterceptor(context, userAgentProvider)
    }

    @Provides
    fun pixelReQueryInterceptor(): PixelReQueryInterceptor {
        return PixelReQueryInterceptor()
    }

    @Provides
    fun pixelEmailRemovalInterceptor(): PixelEmailRemovalInterceptor {
        return PixelEmailRemovalInterceptor()
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
    fun emailService(@Named("nonCaching") retrofit: Retrofit): EmailService =
        retrofit.create(EmailService::class.java)

    @Provides
    fun surrogatesService(@Named("api") retrofit: Retrofit): ResourceSurrogateListService =
        retrofit.create(ResourceSurrogateListService::class.java)

    @Provides
    fun appTrackingProtectionWaitlistService(
        @Named("api") retrofit: Retrofit
    ): AppTrackingProtectionWaitlistService =
        retrofit.create(AppTrackingProtectionWaitlistService::class.java)

    @Provides
    fun brokenSiteSender(
        statisticsStore: StatisticsDataStore,
        variantManager: VariantManager,
        tdsMetadataDao: TdsMetadataDao,
        pixel: Pixel,
        gpc: Gpc,
        featureToggle: FeatureToggle,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): BrokenSiteSender =
        BrokenSiteSubmitter(
            statisticsStore,
            variantManager,
            tdsMetadataDao,
            gpc,
            featureToggle,
            pixel,
            appCoroutineScope)

    @Provides
    fun surveyService(@Named("api") retrofit: Retrofit): SurveyService =
        retrofit.create(SurveyService::class.java)

    @Provides
    fun feedbackSubmitter(
        feedbackService: FeedbackService,
        variantManager: VariantManager,
        apiKeyMapper: SubReasonApiMapper,
        statisticsStore: StatisticsDataStore,
        pixel: Pixel,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): FeedbackSubmitter =
        FireAndForgetFeedbackSubmitter(
            feedbackService,
            variantManager,
            apiKeyMapper,
            statisticsStore,
            pixel,
            appCoroutineScope)

    @Provides
    fun feedbackService(@Named("api") retrofit: Retrofit): FeedbackService =
        retrofit.create(FeedbackService::class.java)

    companion object {
        private const val CACHE_SIZE: Long = 10 * 1024 * 1024 // 10MB
    }
}
