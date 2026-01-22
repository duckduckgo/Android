/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.statistics.user_segments

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.app.statistics.api.StatisticsRequester
import com.duckduckgo.app.statistics.api.StatisticsService
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.statistics.store.StatisticsSharedPreferences
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FileUtilities.loadText
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.data.store.impl.SharedPreferencesProviderImpl
import com.duckduckgo.experiments.api.VariantManager
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

class DuckAiRetentionIntegrationTest {

    @get:Rule
    val schedulers = InstantSchedulersRule()

    @get:Rule
    val coroutineRule: CoroutineTestRule = CoroutineTestRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val server = MockWebServer()

    private lateinit var statisticsService: StatisticsService
    private lateinit var statisticsStore: StatisticsDataStore
    private lateinit var usageHistory: UsageHistory
    private lateinit var testee: StatisticsRequester

    private val crashLogger = object : CrashLogger {
        override fun logCrash(crash: CrashLogger.Crash) {
        }
    }

    private val pixel = object : Pixel {
        override fun fire(
            pixel: Pixel.PixelName,
            parameters: Map<String, String>,
            encodedParameters: Map<String, String>,
            type: Pixel.PixelType,
        ) {
        }

        override fun fire(
            pixelName: String,
            parameters: Map<String, String>,
            encodedParameters: Map<String, String>,
            type: Pixel.PixelType,
        ) {
        }

        override fun enqueueFire(
            pixel: Pixel.PixelName,
            parameters: Map<String, String>,
            encodedParameters: Map<String, String>,
        ) {
        }

        override fun enqueueFire(
            pixelName: String,
            parameters: Map<String, String>,
            encodedParameters: Map<String, String>,
        ) {
        }
    }

    private val mockVariantManager: VariantManager = mock()
    private val mockEmailManager: EmailManager = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()

    @Before
    fun setup() {
        configureStubNetworking()

        statisticsStore = StatisticsSharedPreferences(context)
        statisticsStore.clearAtb()
        context.deleteSharedPreferences(RETENTION_SEGMENTS_PREF_FILE)
        context.deleteSharedPreferences("$RETENTION_SEGMENTS_PREF_FILE.harmony")

        val sharedPreferencesProvider = SharedPreferencesProviderImpl(
            context,
            coroutineRule.testDispatcherProvider,
        ) { crashLogger }

        usageHistory = SegmentStoreModule().provideSegmentStore(
            sharedPreferencesProvider,
            coroutineRule.testDispatcherProvider,
        )

        whenever(mockVariantManager.getVariantKey()).thenReturn("ma")
        runBlocking {
            whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(false)
        }

        val segmentCalculation = RealSegmentCalculation(
            coroutineRule.testDispatcherProvider,
            statisticsStore,
            mockAppBuildConfig,
        )

        val userSegmentsPixelSender = UserSegmentsPixelSender(
            usageHistory,
            segmentCalculation,
            pixel,
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
            crashLogger,
        )

        val plugins = object : PluginPoint<AtbLifecyclePlugin> {
            override fun getPlugins(): Collection<AtbLifecyclePlugin> {
                return listOf(userSegmentsPixelSender)
            }
        }

        testee = StatisticsRequester(
            statisticsStore,
            statisticsService,
            mockVariantManager,
            plugins,
            mockEmailManager,
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
        )
    }

    @After
    fun teardown() {
        server.shutdown()
        context.deleteSharedPreferences(RETENTION_SEGMENTS_PREF_FILE)
        context.deleteSharedPreferences("$RETENTION_SEGMENTS_PREF_FILE.harmony")
    }

    @Test
    fun whenDuckAiRetentionAtbRefreshSucceeds_thenDuckAiHistoryUpdated() = runTest {
        statisticsStore.saveAtb(Atb("100-1"))
        queueResponseFromFile(VALID_REFRESH_RESPONSE_JSON)

        testee.refreshDuckAiRetentionAtb()
        coroutineRule.testScope.advanceUntilIdle()

        assertEquals(listOf("v107-7"), usageHistory.getDuckAiHistory())
    }

    private fun configureStubNetworking() {
        server.start()

        val okHttpClient = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(InetAddress.getLocalHost(), server.port)))
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("localhost/").toString())
            .client(okHttpClient)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()

        statisticsService = retrofit.create(StatisticsService::class.java)
    }

    private fun queueResponseFromFile(
        filename: String,
        responseCode: Int = 200,
    ) {
        val response = MockResponse()
            .setBody(loadText(DuckAiRetentionIntegrationTest::class.java.classLoader!!, "$JSON_DIR/$filename"))
            .setResponseCode(responseCode)

        server.enqueue(response)
    }

    companion object {
        private const val VALID_REFRESH_RESPONSE_JSON = "atb_refresh_response_valid.json"
        private const val JSON_DIR = "json"
        private const val RETENTION_SEGMENTS_PREF_FILE = "com.duckduckgo.mobile.android.retention.usage.history"
    }
}
