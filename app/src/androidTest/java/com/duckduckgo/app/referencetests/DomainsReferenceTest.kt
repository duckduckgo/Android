/*
 * Copyright (c) 2021 DuckDuckGo
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

@file:Suppress("RemoveExplicitTypeArguments")

package com.duckduckgo.app.referencetests

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.FileUtilities
import com.duckduckgo.app.browser.WebViewRequestInterceptor
import com.duckduckgo.app.browser.useragent.provideUserAgentOverridePluginPoint
import com.duckduckgo.app.fakes.FeatureToggleFake
import com.duckduckgo.app.fakes.UserAgentFake
import com.duckduckgo.app.fakes.UserAllowListRepositoryFake
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.privacy.db.UserAllowListDao
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.surrogates.ResourceSurrogateLoader
import com.duckduckgo.app.surrogates.ResourceSurrogatesImpl
import com.duckduckgo.app.surrogates.store.ResourceSurrogateDataStore
import com.duckduckgo.app.trackerdetection.Client
import com.duckduckgo.app.trackerdetection.CloakedCnameDetectorImpl
import com.duckduckgo.app.trackerdetection.EntityLookup
import com.duckduckgo.app.trackerdetection.RealUrlToTypeMapper
import com.duckduckgo.app.trackerdetection.TdsClient
import com.duckduckgo.app.trackerdetection.TdsEntityLookup
import com.duckduckgo.app.trackerdetection.TrackerDetector
import com.duckduckgo.app.trackerdetection.TrackerDetectorImpl
import com.duckduckgo.app.trackerdetection.api.ActionJsonAdapter
import com.duckduckgo.app.trackerdetection.api.TdsJson
import com.duckduckgo.app.trackerdetection.db.TdsCnameEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsDomainEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsEntityDao
import com.duckduckgo.app.trackerdetection.db.WebTrackersBlockedDao
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.httpsupgrade.api.HttpsUpgrader
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.TrackerAllowlist
import com.duckduckgo.privacy.config.api.UserAgent
import com.duckduckgo.request.filterer.api.RequestFilterer
import com.duckduckgo.user.agent.api.UserAgentProvider
import com.duckduckgo.user.agent.impl.RealUserAgentProvider
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(Parameterized::class)
class DomainsReferenceTest(private val testCase: TestCase) {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var entityLookup: EntityLookup
    private lateinit var db: AppDatabase
    private lateinit var trackerDetector: TrackerDetector
    private lateinit var tdsEntityDao: TdsEntityDao
    private lateinit var tdsDomainEntityDao: TdsDomainEntityDao
    private lateinit var tdsCnameEntityDao: TdsCnameEntityDao
    private lateinit var testee: WebViewRequestInterceptor

    private val resourceSurrogates = ResourceSurrogatesImpl()

    private var webView: WebView = mock()
    private val mockUserAllowListDao: UserAllowListDao = mock()
    private val mockContentBlocking: ContentBlocking = mock()
    private val mockTrackerAllowlist: TrackerAllowlist = mock()
    private var mockWebTrackersBlockedDao: WebTrackersBlockedDao = mock()
    private var mockHttpsUpgrader: HttpsUpgrader = mock()
    private var mockRequest: WebResourceRequest = mock()
    private val mockPrivacyProtectionCountDao: PrivacyProtectionCountDao = mock()
    private val mockRequestFilterer: RequestFilterer = mock()
    private val mockUserAllowListRepository: UserAllowListRepository = mock()
    private val fakeUserAgent: UserAgent = UserAgentFake()
    private val fakeToggle: FeatureToggle = FeatureToggleFake()
    private val fakeUserAllowListRepository = UserAllowListRepositoryFake()
    private val userAgentProvider: UserAgentProvider = RealUserAgentProvider(
        { "" },
        mock(),
        provideUserAgentOverridePluginPoint(),
        fakeUserAgent,
        fakeToggle,
        fakeUserAllowListRepository,
        mock(),
    )
    private val mockGpc: Gpc = mock()
    private val mockAdClickManager: AdClickManager = mock()

    companion object {
        private val moshi = Moshi.Builder().add(ActionJsonAdapter()).build()
        private val adapter: JsonAdapter<DomainTest> = moshi.adapter(DomainTest::class.java)

        @JvmStatic
        @Parameterized.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            var domainTests: DomainTest? = null
            val jsonObject: JSONObject = FileUtilities.getJsonObjectFromFile(
                DomainsReferenceTest::class.java.classLoader!!,
                "reference_tests/domain_matching_tests.json",
            )

            jsonObject.keys().forEach {
                if (it == "domainTests") {
                    domainTests = adapter.fromJson(jsonObject.get(it).toString())
                }
            }
            return domainTests?.tests?.filterNot { it.exceptPlatforms?.contains("android-browser") ?: false } ?: emptyList()
        }
    }

    data class DomainTest(
        val name: String,
        val desc: String,
        val tests: List<TestCase>,
    )

    data class TestCase(
        val name: String,
        val siteURL: String,
        val requestURL: String,
        val requestType: String,
        val expectAction: String?,
        val exceptPlatforms: List<String>?,
    )

    @UiThreadTest
    @Before
    fun setup() {
        initialiseTds()
        initialiseResourceSurrogates()
        whenever(mockRequest.isForMainFrame).thenReturn(false)

        testee = WebViewRequestInterceptor(
            trackerDetector = trackerDetector,
            httpsUpgrader = mockHttpsUpgrader,
            resourceSurrogates = resourceSurrogates,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao,
            gpc = mockGpc,
            userAgentProvider = userAgentProvider,
            adClickManager = mockAdClickManager,
            cloakedCnameDetector = CloakedCnameDetectorImpl(tdsCnameEntityDao, mockTrackerAllowlist, mockUserAllowListRepository),
            requestFilterer = mockRequestFilterer,
        )
    }

    @Test
    fun whenReferenceTestRunsItReturnsTheExpectedResult() = runBlocking<Unit> {
        whenever(mockRequest.url).thenReturn(testCase.requestURL.toUri())

        if (testCase.requestType == "script") {
            whenever(mockRequest.requestHeaders).thenReturn(mapOf(Pair("Accept", "application/javascript")))
        }

        if (testCase.requestType == "image") {
            whenever(mockRequest.requestHeaders).thenReturn(mapOf(Pair("Accept", "image/*")))
        }

        if (testCase.requestType == "stylesheet") {
            whenever(mockRequest.requestHeaders).thenReturn(mapOf(Pair("Accept", "text/css")))
        }

        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUrl = testCase.siteURL,
            webView = webView,
            webViewClientListener = null,
        )

        when (testCase.expectAction) {
            null -> {
                assertRequestCanContinueToLoad(response)
            }
            "ignore" -> {
                assertRequestCanContinueToLoad(response)
            }
            "block" -> {
                assertCancelledResponse(response)
            }
        }
    }

    private fun initialiseTds() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        tdsEntityDao = db.tdsEntityDao()
        tdsDomainEntityDao = db.tdsDomainEntityDao()
        tdsCnameEntityDao = db.tdsCnameEntityDao()

        entityLookup = TdsEntityLookup(tdsEntityDao, tdsDomainEntityDao)
        trackerDetector =
            TrackerDetectorImpl(
                entityLookup,
                mockUserAllowListDao,
                mockContentBlocking,
                mockTrackerAllowlist,
                mockWebTrackersBlockedDao,
                mockAdClickManager,
            )

        val json = FileUtilities.loadText(javaClass.classLoader!!, "reference_tests/tracker_radar_reference.json")
        val adapter = moshi.adapter(TdsJson::class.java)
        val tdsJson = adapter.fromJson(json)!!
        val trackers = tdsJson.jsonToTrackers().values.toList()
        val entities = tdsJson.jsonToEntities()
        val domainEntities = tdsJson.jsonToDomainEntities()
        val cnameEntities = tdsJson.jsonToCnameEntities()
        val client = TdsClient(Client.ClientName.TDS, trackers, RealUrlToTypeMapper())

        tdsEntityDao.insertAll(entities)
        tdsDomainEntityDao.insertAll(domainEntities)
        tdsCnameEntityDao.insertAll(cnameEntities)
        trackerDetector.addClient(client)
    }

    private fun initialiseResourceSurrogates() {
        val dataStore = ResourceSurrogateDataStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val resourceSurrogateLoader = ResourceSurrogateLoader(TestScope(), resourceSurrogates, dataStore, coroutinesTestRule.testDispatcherProvider)
        val surrogatesFile = FileUtilities.loadText(javaClass.classLoader!!, "reference_tests/surrogates.txt").toByteArray()
        val surrogates = resourceSurrogateLoader.convertBytes(surrogatesFile)
        resourceSurrogates.loadSurrogates(surrogates)
    }

    private fun assertRequestCanContinueToLoad(response: WebResourceResponse?) {
        assertNull(response)
    }

    private fun assertCancelledResponse(response: WebResourceResponse?) {
        assertNotNull(response)
        assertNull(response!!.data)
        assertNull(response.mimeType)
        assertNull(response.encoding)
    }
}
