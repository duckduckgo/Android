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

@file:Suppress("RemoveExplicitTypeArguments")

package com.duckduckgo.app.referencetests

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.browser.WebViewRequestInterceptor
import com.duckduckgo.app.browser.useragent.provideUserAgentOverridePluginPoint
import com.duckduckgo.app.browser.webview.MaliciousSiteBlockerWebViewIntegration
import com.duckduckgo.app.fakes.FakeMaliciousSiteBlockerWebViewIntegration
import com.duckduckgo.app.fakes.UserAgentFake
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.privacy.db.RealUserAllowListRepository
import com.duckduckgo.app.privacy.db.UserAllowListDao
import com.duckduckgo.app.surrogates.ResourceSurrogateLoader
import com.duckduckgo.app.surrogates.ResourceSurrogatesImpl
import com.duckduckgo.app.surrogates.store.ResourceSurrogateDataStore
import com.duckduckgo.app.trackerdetection.Client
import com.duckduckgo.app.trackerdetection.CloakedCnameDetectorImpl
import com.duckduckgo.app.trackerdetection.EntityLookup
import com.duckduckgo.app.trackerdetection.RealUrlToTypeMapper
import com.duckduckgo.app.trackerdetection.TdsClient
import com.duckduckgo.app.trackerdetection.TdsEntityLookup
import com.duckduckgo.app.trackerdetection.TrackerDetectorClientProvider
import com.duckduckgo.app.trackerdetection.TrackerDetectorImpl
import com.duckduckgo.app.trackerdetection.api.ActionJsonAdapter
import com.duckduckgo.app.trackerdetection.api.TdsJson
import com.duckduckgo.app.trackerdetection.db.TdsCnameEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsDomainEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsEntityDao
import com.duckduckgo.app.trackerdetection.db.WebTrackersBlockedDao
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.feature.toggles.api.FeatureToggleFake
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.httpsupgrade.api.HttpsUpgrader
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.impl.features.contentblocking.RealContentBlocking
import com.duckduckgo.privacy.config.impl.features.trackerallowlist.RealTrackerAllowlist
import com.duckduckgo.privacy.config.impl.features.trackerallowlist.TrackerAllowlistFeature
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.RealUnprotectedTemporary
import com.duckduckgo.privacy.config.store.TrackerAllowlistEntity
import com.duckduckgo.privacy.config.store.features.contentblocking.ContentBlockingRepository
import com.duckduckgo.privacy.config.store.features.trackerallowlist.TrackerAllowlistRepository
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.UnprotectedTemporaryRepository
import com.duckduckgo.request.filterer.api.RequestFilterer
import com.duckduckgo.request.interception.impl.RealRequestBlocklist
import com.duckduckgo.request.interception.impl.RequestBlocklistFeature
import com.duckduckgo.tracker.detection.api.TrackerDetector
import com.duckduckgo.user.agent.api.UserAgentProvider
import com.duckduckgo.user.agent.impl.RealUserAgentProvider
import com.duckduckgo.user.agent.impl.UserAgent
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.flowOf
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
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(Parameterized::class)
class RequestBlocklistReferenceTest(private val testCase: TestCase) {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var entityLookup: EntityLookup
    private lateinit var db: AppDatabase
    private lateinit var trackerDetector: TrackerDetector
    private lateinit var trackerDetectorClientProvider: TrackerDetectorClientProvider
    private lateinit var tdsEntityDao: TdsEntityDao
    private lateinit var tdsDomainEntityDao: TdsDomainEntityDao
    private lateinit var tdsCnameEntityDao: TdsCnameEntityDao
    private lateinit var trackerAllowlist: RealTrackerAllowlist
    private lateinit var userAllowListRepository: RealUserAllowListRepository
    private lateinit var userAgentProvider: UserAgentProvider
    private lateinit var testee: WebViewRequestInterceptor

    private val resourceSurrogates = ResourceSurrogatesImpl()
    private var webView: WebView = mock()
    private val userAllowlistDao: UserAllowListDao = mock()
    private val contentBlockingRepository: ContentBlockingRepository = mock()
    private val unprotectedTemporaryRepository: UnprotectedTemporaryRepository = mock()
    private val trackerAllowlistRepository: TrackerAllowlistRepository = mock()
    private lateinit var contentBlocking: RealContentBlocking
    private var mockWebTrackersBlockedDao: WebTrackersBlockedDao = mock()
    private var mockHttpsUpgrader: HttpsUpgrader = mock()
    private var mockRequest: WebResourceRequest = mock()
    private val mockPrivacyProtectionCountDao: PrivacyProtectionCountDao = mock()
    private val mockRequestFilterer: RequestFilterer = mock()
    private val mockDuckPlayer: DuckPlayer = mock()
    private val requestBlocklistFeature: RequestBlocklistFeature = FakeFeatureToggleFactory.create(RequestBlocklistFeature::class.java)
    private val fakeUserAgent: UserAgent = UserAgentFake()

    @Suppress("DEPRECATION")
    private val fakeToggle: FeatureToggle = FeatureToggleFake()
    private val mockGpc: Gpc = mock()
    private val mockAdClickManager: AdClickManager = mock()
    private val mockMaliciousSiteProtection: MaliciousSiteBlockerWebViewIntegration = FakeMaliciousSiteBlockerWebViewIntegration()
    private val fakeAndroidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private val moshi = Moshi.Builder().add(ActionJsonAdapter()).build()

    companion object {
        private val moshi = Moshi.Builder().build()
        private val adapter: JsonAdapter<ReferenceTests> = moshi.adapter(ReferenceTests::class.java)

        @JvmStatic
        @Parameterized.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            val referenceTests = adapter.fromJson(
                FileUtilities.loadText(
                    RequestBlocklistReferenceTest::class.java.classLoader!!,
                    "reference_tests/request-blocklist/tests.json",
                ),
            )
            val allTests = mutableListOf<TestCase>()
            referenceTests?.basic?.tests?.let { allTests.addAll(it) }
            referenceTests?.allowlisting?.tests?.let { allTests.addAll(it) }
            referenceTests?.trackers?.tests?.let { allTests.addAll(it) }
            referenceTests?.incorrect?.tests?.let { allTests.addAll(it) }
            referenceTests?.ordering?.tests?.let { allTests.addAll(it) }
            return allTests.filterNot { it.exceptPlatforms.contains("android-browser") }
        }
    }

    data class TestCase(
        val name: String,
        val requestUrl: String,
        val requestType: String,
        val websiteUrl: String,
        val expectAction: String,
        val exceptPlatforms: List<String>,
    )

    data class TestSection(
        val name: String,
        val desc: String,
        val tests: List<TestCase>,
    )

    data class ReferenceTests(
        val basic: TestSection?,
        val allowlisting: TestSection?,
        val trackers: TestSection?,
        val incorrect: TestSection?,
        val ordering: TestSection?,
    )

    @UiThreadTest
    @Before
    fun setup() {
        val configObject = JSONObject(
            FileUtilities.loadText(
                javaClass.classLoader!!,
                "reference_tests/request-blocklist/config-reference.json",
            ),
        )

        initialiseResourceSurrogates()
        setupUserAllowList()
        userAgentProvider = RealUserAgentProvider(
            { "" },
            mock(),
            provideUserAgentOverridePluginPoint(),
            fakeUserAgent,
            fakeToggle,
            userAllowListRepository,
        )
        setupTrackerAllowlist(configObject)
        setupContentBlocking(configObject)
        initialiseTds()

        val requestBlocklist = createRequestBlocklist(configObject)

        testee = WebViewRequestInterceptor(
            trackerDetector = trackerDetector,
            httpsUpgrader = mockHttpsUpgrader,
            resourceSurrogates = resourceSurrogates,
            privacyProtectionCountDao = mockPrivacyProtectionCountDao,
            gpc = mockGpc,
            userAgentProvider = userAgentProvider,
            adClickManager = mockAdClickManager,
            cloakedCnameDetector = CloakedCnameDetectorImpl(tdsCnameEntityDao, trackerAllowlist, userAllowListRepository),
            requestFilterer = mockRequestFilterer,
            requestBlocklist = requestBlocklist,
            contentBlocking = contentBlocking,
            trackerAllowlist = trackerAllowlist,
            userAllowListRepository = userAllowListRepository,
            duckPlayer = mockDuckPlayer,
            maliciousSiteBlockerWebViewIntegration = mockMaliciousSiteProtection,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            androidBrowserConfigFeature = fakeAndroidBrowserConfigFeature,
            appCoroutineScope = coroutinesTestRule.testScope,
            isMainProcess = true,
        )
    }

    @Test
    fun whenReferenceTestRunsItReturnsTheExpectedResult() = runBlocking<Unit> {
        whenever(mockRequest.url).thenReturn(testCase.requestUrl.toUri())
        whenever(mockRequest.isForMainFrame).thenReturn(testCase.requestType == "main_frame")

        whenever(mockRequest.requestHeaders).thenReturn(emptyMap())

        val response = testee.shouldIntercept(
            request = mockRequest,
            documentUri = testCase.websiteUrl.toUri(),
            webView = webView,
            webViewClientListener = null,
        )

        when (testCase.expectAction) {
            "block" -> assertCancelledResponse(response)
            "allow" -> assertRequestCanContinueToLoad(response)
        }
    }

    @SuppressLint("DenyListedApi")
    private fun createRequestBlocklist(configObject: JSONObject): RealRequestBlocklist {
        val requestBlocklistJson = configObject.getJSONObject("features").getJSONObject("requestBlocklist")
        val settings = requestBlocklistJson.getJSONObject("settings").toString()

        val exceptions = mutableListOf<FeatureException>()
        requestBlocklistJson.optJSONArray("exceptions")?.let { arr ->
            for (i in 0 until arr.length()) {
                exceptions.add(FeatureException(arr.getJSONObject(i).getString("domain"), null))
            }
        }

        requestBlocklistFeature.self().setRawStoredState(
            Toggle.State(
                remoteEnableState = true,
                enable = true,
                settings = settings,
                exceptions = exceptions,
            ),
        )

        val requestBlocklist = RealRequestBlocklist(
            requestBlocklistFeature = requestBlocklistFeature,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            isMainProcess = true,
            appCoroutineScope = coroutinesTestRule.testScope,
            moshi = moshi,
        )
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()
        return requestBlocklist
    }

    private fun initialiseTds() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        tdsEntityDao = db.tdsEntityDao()
        tdsDomainEntityDao = db.tdsDomainEntityDao()
        tdsCnameEntityDao = db.tdsCnameEntityDao()

        entityLookup = TdsEntityLookup(tdsEntityDao, tdsDomainEntityDao)
        val trackerDetectorImpl = TrackerDetectorImpl(
            entityLookup,
            userAllowlistDao,
            contentBlocking,
            trackerAllowlist,
            mockWebTrackersBlockedDao,
            mockAdClickManager,
        )
        trackerDetector = trackerDetectorImpl
        trackerDetectorClientProvider = trackerDetectorImpl

        val json = FileUtilities.loadText(javaClass.classLoader!!, "reference_tests/request-blocklist/tds-reference.json")
        val adapter = moshi.adapter(TdsJson::class.java)
        val tdsJson = adapter.fromJson(json)!!
        val trackers = tdsJson.jsonToTrackers().values.toList()
        val entities = tdsJson.jsonToEntities()
        val domainEntities = tdsJson.jsonToDomainEntities()
        val cnameEntities = tdsJson.jsonToCnameEntities()
        val client = TdsClient(Client.ClientName.TDS, trackers, RealUrlToTypeMapper(), false)

        tdsEntityDao.insertAll(entities)
        tdsDomainEntityDao.insertAll(domainEntities)
        tdsCnameEntityDao.insertAll(cnameEntities)
        trackerDetectorClientProvider.addClient(client)
    }

    private fun initialiseResourceSurrogates() {
        val dataStore = ResourceSurrogateDataStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val resourceSurrogateLoader = ResourceSurrogateLoader(TestScope(), resourceSurrogates, dataStore, coroutinesTestRule.testDispatcherProvider)
        val surrogatesFile = FileUtilities.loadText(
            javaClass.classLoader!!,
            "reference_tests/request-blocklist/surrogates-reference.txt",
        ).toByteArray()
        val surrogates = resourceSurrogateLoader.convertBytes(surrogatesFile)
        resourceSurrogates.loadSurrogates(surrogates)
    }

    private fun setupContentBlocking(configObject: JSONObject) {
        val contentBlockingExceptions = mutableListOf<FeatureException>()
        val unprotectedTemporaryExceptions = mutableListOf<FeatureException>()
        val features = configObject.getJSONObject("features")

        features.optJSONObject("contentBlocking")?.optJSONArray("exceptions")?.let { arr ->
            for (i in 0 until arr.length()) {
                contentBlockingExceptions.add(FeatureException(arr.getJSONObject(i).getString("domain"), null))
            }
        }

        configObject.optJSONArray("unprotectedTemporary")?.let { arr ->
            for (i in 0 until arr.length()) {
                unprotectedTemporaryExceptions.add(FeatureException(arr.getJSONObject(i).getString("domain"), null))
            }
        }

        whenever(unprotectedTemporaryRepository.exceptions).thenReturn(CopyOnWriteArrayList(unprotectedTemporaryExceptions))
        val unprotectedTemporary = RealUnprotectedTemporary(unprotectedTemporaryRepository)

        whenever(contentBlockingRepository.exceptions).thenReturn(CopyOnWriteArrayList(contentBlockingExceptions))
        contentBlocking = RealContentBlocking(contentBlockingRepository, fakeToggle, unprotectedTemporary)
    }

    private fun setupTrackerAllowlist(configObject: JSONObject) {
        val jsonString = configObject.getJSONObject("features").getJSONObject("trackerAllowlist").toString()
        val jsonAdapter: JsonAdapter<TrackerAllowlistFeature> = moshi.adapter(TrackerAllowlistFeature::class.java)
        val feature = jsonAdapter.fromJson(jsonString)

        val allowlistEntries = feature?.settings?.allowlistedTrackers?.entries?.map { entry ->
            TrackerAllowlistEntity(entry.key, entry.value.rules)
        } ?: emptyList()

        whenever(trackerAllowlistRepository.exceptions).thenReturn(CopyOnWriteArrayList(allowlistEntries))
        trackerAllowlist = RealTrackerAllowlist(trackerAllowlistRepository, fakeToggle)
    }

    private fun setupUserAllowList() {
        val listType = Types.newParameterizedType(List::class.java, String::class.java)
        val userAllowList: List<String> = moshi.adapter<List<String>>(listType).fromJson(
            FileUtilities.loadText(javaClass.classLoader!!, "reference_tests/request-blocklist/user-allowlist-reference.json"),
        ) ?: emptyList()

        whenever(userAllowlistDao.allDomainsFlow()).thenReturn(flowOf(userAllowList))
        userAllowListRepository = RealUserAllowListRepository(
            userAllowListDao = userAllowlistDao,
            appCoroutineScope = coroutinesTestRule.testScope,
            dispatcherProvider = coroutinesTestRule.testDispatcherProvider,
            isMainProcess = true,
        )
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()
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
