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
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.statistics.store.StatisticsSharedPreferences
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.squareup.moshi.Moshi
import io.reactivex.Observable
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
        private const val INVALID_JSON_MISSING_VERSION = "atb_response_invalid_missing_version.json"
        private const val INVALID_JSON_CORRUPT_JSON = "atb_response_invalid_malformed_json.json"

        private const val JSON_DIR = "json"
    }
}

private fun <T> Observable<T>.testObserve(): T {
    return this.test().also {
        it.awaitTerminalEvent()
        it.assertNoErrors()
        it.assertValueCount(1)
    }.values().first()
}
