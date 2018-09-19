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

import android.support.test.InstrumentationRegistry
import com.duckduckgo.app.FileUtilities.loadText
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.global.AppUrl.ParamKey
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.statistics.store.StatisticsSharedPreferences
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory


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

        statisticsStore = StatisticsSharedPreferences(InstrumentationRegistry.getTargetContext())
        statisticsStore.clearAtb()

        testee = StatisticsRequester(statisticsStore, statisticsService, mockVariantManager)
        whenever(mockVariantManager.getVariant()).thenReturn(Variant("ma", 100.0))
    }

    @After
    fun tearDown() {
        server.shutdown()
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
        val request = server.takeRequest()
        assertEquals("/atb.js", request.requestUrl.encodedPath())
    }

    @Test
    fun whenNotYetInitializedAtbInitializationSendsTestParameter() {
        queueResponseFromFile(VALID_JSON)
        queueResponseFromString("", 200)
        testee.initializeAtb()
        val atbRequest = server.takeRequest()
        val testParam = atbRequest.requestUrl.queryParameter(ParamKey.DEV_MODE)
        assertTestParameterSent(testParam)
    }

    @Test
    fun whenNotYetInitializedExtiInitializationRetrievesFromCorrectEndpoint() {
        queueResponseFromFile(VALID_JSON)
        queueResponseFromString("", 200)
        testee.initializeAtb()
        server.takeRequest()
        val extiRequest = server.takeRequest()
        assertEquals("/exti/", extiRequest.requestUrl.encodedPath())
    }

    @Test
    fun whenNotYetInitializedExtiInitializationSendsTestParameter() {
        queueResponseFromFile(VALID_JSON)
        queueResponseFromString("", 200)
        testee.initializeAtb()
        server.takeRequest()
        val extiRequest = server.takeRequest()
        val testParam = extiRequest.requestUrl.queryParameter(ParamKey.DEV_MODE)
        assertTestParameterSent(testParam)
    }

    @Test
    fun whenNotYetInitializedExtiInitializationSendsCorrectAtb() {
        queueResponseFromFile(VALID_JSON)
        queueResponseFromString("", 200)
        testee.initializeAtb()
        server.takeRequest()
        val extiRequest = server.takeRequest()
        val atbQueryParam = extiRequest.requestUrl.queryParameter("atb")
        assertNotNull(atbQueryParam)
        assertEquals("v105-3ma", atbQueryParam)
    }

    @Test
    fun whenAlreadyInitializedRefreshCallGoesToCorrectEndpoint() {
        statisticsStore.saveAtb(Atb("100-1"))
        queueResponseFromFile(VALID_REFRESH_RESPONSE_JSON)
        testee.refreshRetentionAtb()
        val refreshRequest = server.takeRequest()
        assertEquals("/atb.js", refreshRequest.requestUrl.encodedPath())
    }

    @Test
    fun whenAlreadyInitializedRefreshCallUpdatesRetentionAtb() {
        statisticsStore.saveAtb(Atb("100-1"))
        queueResponseFromFile(VALID_REFRESH_RESPONSE_JSON)
        testee.refreshRetentionAtb()
        assertEquals("v107-7", statisticsStore.retentionAtb)
    }

    @Test
    fun whenAlreadyInitializedRefreshCallSendsTestParameter() {
        statisticsStore.saveAtb(Atb("100-1"))
        queueResponseFromFile(VALID_REFRESH_RESPONSE_JSON)
        testee.refreshRetentionAtb()
        val refreshRequest = server.takeRequest()
        val testParam = refreshRequest.requestUrl.queryParameter(ParamKey.DEV_MODE)
        assertTestParameterSent(testParam)
    }

    @Test
    fun whenAlreadyInitializedRefreshCallSendsCorrectAtb() {
        statisticsStore.saveAtb(Atb("100-1"))
        queueResponseFromFile(VALID_REFRESH_RESPONSE_JSON)
        testee.refreshRetentionAtb()
        val refreshRequest = server.takeRequest()
        val atbParam = refreshRequest.requestUrl.queryParameter(ParamKey.ATB)
        assertEquals("100-1ma", atbParam)
    }

    @Test
    fun whenAlreadyInitializedRefreshCallSendsCorrectRetentionAtb() {
        statisticsStore.saveAtb(Atb("100-1"))
        statisticsStore.retentionAtb = "101-3"
        queueResponseFromFile(VALID_REFRESH_RESPONSE_JSON)
        testee.refreshRetentionAtb()
        val refreshRequest = server.takeRequest()
        val atbParam = refreshRequest.requestUrl.queryParameter(ParamKey.RETENTION_ATB)
        assertEquals("101-3", atbParam)
    }

    private fun assertTestParameterSent(testParam: String?) {
        assertNotNull(testParam)
        assertEquals("1", testParam)
    }

    private fun queueResponseFromFile(filename: String, responseCode: Int = 200) {
        val response = MockResponse()
            .setBody(loadText("$JSON_DIR/$filename"))
            .setResponseCode(responseCode)

        queueResponse(response)
    }

    private fun queueResponseFromString(responseBody: String, responseCode: Int = 200) {
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
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/").toString())
            .client(okHttpClient)
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()

        statisticsService = retrofit.create(StatisticsService::class.java)
    }

    private fun assertNumberRequestsMade(expectedRequests: Int) {
        assertEquals(expectedRequests, server.requestCount)
    }

    companion object {
        private const val VALID_JSON = "atb_response_valid.json"
        private const val VALID_REFRESH_RESPONSE_JSON = "atb_refresh_response_valid.json"
        private const val INVALID_JSON_MISSING_VERSION = "atb_response_invalid_missing_version.json"
        private const val INVALID_JSON_CORRUPT_JSON = "atb_response_invalid_malformed_json.json"

        private const val JSON_DIR = "json"
    }
}
