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

package com.duckduckgo.app.statistics.api

import android.net.Uri
import androidx.core.net.toUri
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.FileUtilities.loadText
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.global.AppUrl.ParamKey
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.statistics.store.StatisticsSharedPreferences
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class StatisticsRequesterJsonTest {

    private var mockVariantManager: VariantManager = mock()

    private lateinit var statisticsService: StatisticsService
    private lateinit var statisticsStore: StatisticsDataStore
    private lateinit var testee: StatisticsRequester

    private val server = MockWebServer()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @Before
    fun before() {
        configureStubNetworking()

        statisticsStore = StatisticsSharedPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        statisticsStore.clearAtb()

        val plugins = object : PluginPoint<RefreshRetentionAtbPlugin> {
            override fun getPlugins(): Collection<RefreshRetentionAtbPlugin> {
                return listOf()
            }
        }
        testee = StatisticsRequester(statisticsStore, statisticsService, mockVariantManager, plugins)
        whenever(mockVariantManager.getVariant()).thenReturn(Variant("ma", 100.0, filterBy = { true }))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun whenAlreadyInitializedRefreshSearchRetentionCallWithUpdateVersionResponseUpdatesAtb() {
        statisticsStore.saveAtb(Atb("100-1"))
        queueResponseFromFile(VALID_UPDATE_RESPONSE_JSON)
        testee.refreshSearchRetentionAtb()
        assertEquals("v99-1", statisticsStore.atb?.version)
    }

    @Test
    fun whenAlreadyInitializedRefreshAppRetentionCallWithUpdateVersionResponseUpdatesAtb() {
        statisticsStore.saveAtb(Atb("100-1"))
        queueResponseFromFile(VALID_UPDATE_RESPONSE_JSON)
        testee.refreshAppRetentionAtb()
        assertEquals("v99-1", statisticsStore.atb?.version)
    }

    @Test
    fun whenNotYetInitializedAtbInitializationStoresAtbResponse() {
        queueResponseFromFile(VALID_JSON)
        queueResponseFromString(responseBody = "", responseCode = 200)
        testee.initializeAtb()
        assertEquals("v105-3", statisticsStore.atb?.version)
        assertNumberRequestsMade(2)
    }

    @Test
    fun whenNotYetInitializedAtbInitializationResultsInStoredStats() {
        queueResponseFromFile(VALID_JSON)
        queueResponseFromString(responseBody = "", responseCode = 200)
        testee.initializeAtb()
        assertTrue(statisticsStore.hasInstallationStatistics)
        assertNumberRequestsMade(2)
    }

    @Test
    fun whenNotYetInitializedAndAtbInitializationHasMissingRequiredJsonFieldThenNoStatsStored() {
        queueResponseFromFile(INVALID_JSON_MISSING_VERSION)
        testee.initializeAtb()
        assertFalse(statisticsStore.hasInstallationStatistics)
        assertNumberRequestsMade(1)
    }

    @Test
    fun whenNotYetInitializedAndAtbInitializationResponseIsCorruptThenNoStatsStored() {
        queueResponseFromFile(INVALID_JSON_CORRUPT_JSON)
        queueResponseFromString(responseBody = "", responseCode = 200)
        testee.initializeAtb()
        assertFalse(statisticsStore.hasInstallationStatistics)
        assertNumberRequestsMade(1)
    }

    @Test
    fun whenNotYetInitializedAndExtiCallErrorsThenNoStatsStored() {
        queueResponseFromFile(VALID_JSON)
        queueError()
        testee.initializeAtb()
        assertFalse(statisticsStore.hasInstallationStatistics)
        assertNumberRequestsMade(2)
    }

    @Test
    fun whenAlreadyInitializedAtbInitializationDoesNotReInitialize() {
        statisticsStore.saveAtb(Atb("123"))
        testee.initializeAtb()
        assertNumberRequestsMade(0)
    }

    @Test
    fun whenNotYetInitializedAtbInitializationRetrievesFromCorrectEndpoint() {
        queueResponseFromFile(VALID_JSON)
        queueResponseFromString("", 200)
        testee.initializeAtb()
        val request = takeRequestImmediately()
        assertEquals("/atb.js", request?.encodedPath())
    }

    @Test
    fun whenNotYetInitializedAtbInitializationSendsTestParameter() {
        queueResponseFromFile(VALID_JSON)
        queueResponseFromString("", 200)
        testee.initializeAtb()
        val atbRequest = takeRequestImmediately()
        val testParam = atbRequest?.extractQueryParam(ParamKey.DEV_MODE)
        assertTestParameterSent(testParam)
    }

    @Test
    fun whenNotYetInitializedExtiInitializationRetrievesFromCorrectEndpoint() {
        queueResponseFromFile(VALID_JSON)
        queueResponseFromString("", 200)
        testee.initializeAtb()
        takeRequestImmediately()
        val extiRequest = takeRequestImmediately()
        assertEquals("/exti/", extiRequest?.encodedPath())
    }

    @Test
    fun whenNotYetInitializedExtiInitializationSendsTestParameter() {
        queueResponseFromFile(VALID_JSON)
        queueResponseFromString("", 200)
        testee.initializeAtb()
        takeRequestImmediately()
        val extiRequest = takeRequestImmediately()
        val testParam = extiRequest?.extractQueryParam(ParamKey.DEV_MODE)
        assertTestParameterSent(testParam)
    }

    @Test
    fun whenNotYetInitializedExtiInitializationSendsCorrectAtb() {
        queueResponseFromFile(VALID_JSON)
        queueResponseFromString("", 200)
        testee.initializeAtb()
        takeRequestImmediately()
        val extiRequest = takeRequestImmediately()
        val atbQueryParam = extiRequest?.extractQueryParam("atb")
        assertNotNull(atbQueryParam)
        assertEquals("v105-3ma", atbQueryParam)
    }

    @Test
    fun whenAlreadyInitializedRefreshSearchCallGoesToCorrectEndpoint() {
        statisticsStore.saveAtb(Atb("100-1"))
        queueResponseFromFile(VALID_REFRESH_RESPONSE_JSON)
        testee.refreshSearchRetentionAtb()
        val refreshRequest = takeRequestImmediately()
        assertEquals("/atb.js", refreshRequest?.encodedPath())
        assertNull(refreshRequest?.extractQueryParam("at"))
    }

    @Test
    fun whenAlreadyInitializedRefreshAppCallGoesToCorrectEndpoint() {
        statisticsStore.saveAtb(Atb("100-1"))
        queueResponseFromFile(VALID_REFRESH_RESPONSE_JSON)
        testee.refreshAppRetentionAtb()
        val refreshRequest = takeRequestImmediately()
        assertEquals("/atb.js", refreshRequest?.encodedPath())
        assertEquals("app_use", refreshRequest?.extractQueryParam("at"))
    }

    @Test
    fun whenAlreadyInitializedRefreshSearchCallUpdatesSearchRetentionAtb() {
        statisticsStore.saveAtb(Atb("100-1"))
        queueResponseFromFile(VALID_REFRESH_RESPONSE_JSON)
        testee.refreshSearchRetentionAtb()
        assertEquals("v107-7", statisticsStore.searchRetentionAtb)
    }

    @Test
    fun whenAlreadyInitializedRefreshAppCallUpdatesAppRetentionAtb() {
        statisticsStore.saveAtb(Atb("100-1"))
        queueResponseFromFile(VALID_REFRESH_RESPONSE_JSON)
        testee.refreshAppRetentionAtb()
        assertEquals("v107-7", statisticsStore.appRetentionAtb)
    }

    @Test
    fun whenAlreadyInitializedRefreshSearchCallSendsTestParameter() {
        statisticsStore.saveAtb(Atb("100-1"))
        queueResponseFromFile(VALID_REFRESH_RESPONSE_JSON)
        testee.refreshSearchRetentionAtb()
        val refreshRequest = takeRequestImmediately()
        val testParam = refreshRequest?.extractQueryParam(ParamKey.DEV_MODE)
        assertTestParameterSent(testParam)
    }

    @Test
    fun whenAlreadyInitializedRefreshAppCallSendsTestParameter() {
        statisticsStore.saveAtb(Atb("100-1"))
        queueResponseFromFile(VALID_REFRESH_RESPONSE_JSON)
        testee.refreshAppRetentionAtb()
        val refreshRequest = takeRequestImmediately()
        val testParam = refreshRequest?.extractQueryParam(ParamKey.DEV_MODE)
        assertTestParameterSent(testParam)
    }

    @Test
    fun whenAlreadyInitializedRefreshSearchCallSendsCorrectAtb() {
        statisticsStore.saveAtb(Atb("100-1"))
        queueResponseFromFile(VALID_REFRESH_RESPONSE_JSON)
        testee.refreshSearchRetentionAtb()
        val refreshRequest = takeRequestImmediately()
        val atbParam = refreshRequest?.extractQueryParam(ParamKey.ATB)
        assertEquals("100-1ma", atbParam)
    }

    @Test
    fun whenAlreadyInitializedRefreshAppCallSendsCorrectAtb() {
        statisticsStore.saveAtb(Atb("100-1"))
        queueResponseFromFile(VALID_REFRESH_RESPONSE_JSON)
        testee.refreshAppRetentionAtb()
        val refreshRequest = takeRequestImmediately()
        val atbParam = refreshRequest?.extractQueryParam(ParamKey.ATB)
        assertEquals("100-1ma", atbParam)
    }

    @Test
    fun whenAlreadyInitializedRefreshSearchCallSendsCorrectRetentionAtb() {
        statisticsStore.saveAtb(Atb("100-1"))
        statisticsStore.searchRetentionAtb = "101-3"
        queueResponseFromFile(VALID_REFRESH_RESPONSE_JSON)
        testee.refreshSearchRetentionAtb()
        val refreshRequest = takeRequestImmediately()
        val atbParam = refreshRequest?.extractQueryParam(ParamKey.RETENTION_ATB)
        assertEquals("101-3", atbParam)
    }

    @Test
    fun whenAlreadyInitializedRefreshAppCallSendsCorrectRetentionAtb() {
        statisticsStore.saveAtb(Atb("100-1"))
        statisticsStore.appRetentionAtb = "101-3"
        queueResponseFromFile(VALID_REFRESH_RESPONSE_JSON)
        testee.refreshAppRetentionAtb()
        val refreshRequest = takeRequestImmediately()
        val atbParam = refreshRequest?.extractQueryParam(ParamKey.RETENTION_ATB)
        assertEquals("101-3", atbParam)
    }

    /**
     * Should there be an issue obtaining the request, this will avoid the tests stalling indefinitely.
     *
     * If it takes more than the specified time to obtain the request, it's probably a developer error in configuring the test.
     *
     * However, it has been observed that the request could stall indefinitely when working with a device with Charles proxy configured.
     */
    private fun takeRequestImmediately() = server.takeRequest(100, TimeUnit.MILLISECONDS)

    private fun assertTestParameterSent(testParam: String?) {
        assertNotNull(testParam)
        assertEquals("1", testParam)
    }

    private fun queueResponseFromFile(
        filename: String,
        responseCode: Int = 200
    ) {
        val response = MockResponse()
            .setBody(loadText("$JSON_DIR/$filename"))
            .setResponseCode(responseCode)

        queueResponse(response)
    }

    @Suppress("SameParameterValue")
    private fun queueResponseFromString(
        responseBody: String,
        responseCode: Int = 200
    ) {
        val response = MockResponse()
            .setBody(responseBody)
            .setResponseCode(responseCode)

        queueResponse(response)
    }

    private fun queueError() {
        server.enqueue(MockResponse().setResponseCode(400))
    }

    private fun queueResponse(response: MockResponse) = server.enqueue(response)

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

    private fun assertNumberRequestsMade(expectedRequests: Int) {
        assertEquals(expectedRequests, server.requestCount)
    }

    private fun RecordedRequest.requestUri(): Uri {
        return requestLine.toUri()
    }

    private fun RecordedRequest.extractQueryParam(keyName: String): String? {
        return toString().split(" ")[1].toUri().getQueryParameter(keyName)
    }

    private fun RecordedRequest.encodedPath(): String? {
        return requestUri().encodedPath
    }

    companion object {
        private const val VALID_JSON = "atb_response_valid.json"
        private const val VALID_REFRESH_RESPONSE_JSON = "atb_refresh_response_valid.json"
        private const val VALID_UPDATE_RESPONSE_JSON = "atb_update_response_valid.json"
        private const val INVALID_JSON_MISSING_VERSION = "atb_response_invalid_missing_version.json"
        private const val INVALID_JSON_CORRUPT_JSON = "atb_response_invalid_malformed_json.json"

        private const val JSON_DIR = "json"
    }
}
