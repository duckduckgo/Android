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
import com.duckduckgo.app.feedback.api.FeedbackService
import com.duckduckgo.app.feedback.api.FeedbackSubmitter
import com.duckduckgo.app.feedback.api.FireAndForgetFeedbackSubmitter
import com.duckduckgo.app.feedback.api.SubReasonApiMapper
import com.duckduckgo.app.global.api.*
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.AppUrl.Url
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.common.utils.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.user.agent.api.UserAgentProvider
import com.squareup.moshi.Moshi
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import logcat.LogPriority.VERBOSE
import logcat.logcat
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import javax.inject.Named

@Module
class NetworkModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    @Named("api")
    fun apiOkHttpClient(
        context: Context,
        apiRequestInterceptor: ApiRequestInterceptor,
        apiInterceptorPlugins: PluginPoint<ApiInterceptorPlugin>,
    ): OkHttpClient {
        val cacheLocation = File(context.cacheDir, NetworkApiCache.FILE_NAME)
        val cache = Cache(cacheLocation, CACHE_SIZE)
        return OkHttpClient.Builder()
            .addInterceptor(apiRequestInterceptor)
            .cache(cache).apply {
                apiInterceptorPlugins.getPlugins().forEach {
                    addInterceptor(it.getInterceptor())
                }
            }
            // See https://app.asana.com/0/1202552961248957/1204588257103865/f and
            // https://github.com/square/okhttp/issues/6877#issuecomment-1438554879
            .proxySelector(
                object : ProxySelector() {
                    override fun select(uri: URI?): List<Proxy> {
                        return try {
                            getDefault().select(uri)
                        } catch (t: Throwable) {
                            listOf(Proxy.NO_PROXY)
                        }
                    }

                    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                        getDefault().connectFailed(uri, sa, ioe)
                    }
                },
            )
            .build()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    @Named("nonCaching")
    fun pixelOkHttpClient(
        apiRequestInterceptor: ApiRequestInterceptor,
        pixelReQueryInterceptor: PixelReQueryInterceptor,
        pixelInterceptorPlugins: PluginPoint<PixelInterceptorPlugin>,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(apiRequestInterceptor)
            .addInterceptor(pixelReQueryInterceptor)
            .apply {
                pixelInterceptorPlugins.getPlugins().forEach { addInterceptor(it.getInterceptor()) }
            }
            // shall be the last one as it is logging the pixel request url that goes out
            .addInterceptor { chain: Interceptor.Chain ->
                logcat(VERBOSE) { "Pixel url request: ${chain.request().url}" }
                return@addInterceptor chain.proceed(chain.request())
            }
            .build()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    @Named("api")
    fun apiRetrofit(
        @Named("api") okHttpClient: Lazy<OkHttpClient>,
        moshi: Moshi,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Url.API)
            .callFactory { okHttpClient.get().newCall(it) }
            .addConverterFactory(ScalarsConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    @Named("nonCaching")
    fun nonCachingRetrofit(
        @Named("nonCaching") okHttpClient: Lazy<OkHttpClient>,
        moshi: Moshi,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Url.API)
            .callFactory { okHttpClient.get().newCall(it) }
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    fun apiRequestInterceptor(
        context: Context,
        userAgentProvider: UserAgentProvider,
        appBuildConfig: AppBuildConfig,
    ): ApiRequestInterceptor {
        return ApiRequestInterceptor(context, userAgentProvider, appBuildConfig)
    }

    @Provides
    fun pixelReQueryInterceptor(): PixelReQueryInterceptor {
        return PixelReQueryInterceptor()
    }

    @Provides
    fun feedbackSubmitter(
        feedbackService: FeedbackService,
        variantManager: VariantManager,
        apiKeyMapper: SubReasonApiMapper,
        statisticsStore: StatisticsDataStore,
        pixel: Pixel,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        appBuildConfig: AppBuildConfig,
        dispatcherProvider: DispatcherProvider,
    ): FeedbackSubmitter =
        FireAndForgetFeedbackSubmitter(
            feedbackService,
            variantManager,
            apiKeyMapper,
            statisticsStore,
            pixel,
            appCoroutineScope,
            appBuildConfig,
            dispatcherProvider,
        )

    companion object {
        private const val CACHE_SIZE: Long = 10 * 1024 * 1024 // 10MB
    }
}
