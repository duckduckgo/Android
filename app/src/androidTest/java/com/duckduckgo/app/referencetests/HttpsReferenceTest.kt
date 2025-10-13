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

package com.duckduckgo.app.referencetests

import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.trackerdetection.api.ActionJsonAdapter
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.common.utils.isHttps
import com.duckduckgo.common.utils.store.BinaryDataStore
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.httpsupgrade.api.HttpsEmbeddedDataPersister
import com.duckduckgo.httpsupgrade.api.HttpsUpgrader
import com.duckduckgo.httpsupgrade.impl.HttpsBloomFilterFactory
import com.duckduckgo.httpsupgrade.impl.HttpsBloomFilterFactoryImpl
import com.duckduckgo.httpsupgrade.impl.HttpsDataPersister
import com.duckduckgo.httpsupgrade.impl.HttpsFalsePositivesJsonAdapter
import com.duckduckgo.httpsupgrade.impl.HttpsUpgraderImpl
import com.duckduckgo.httpsupgrade.store.HttpsBloomFilterSpec
import com.duckduckgo.httpsupgrade.store.HttpsBloomFilterSpecDao
import com.duckduckgo.httpsupgrade.store.HttpsFalsePositiveDomain
import com.duckduckgo.httpsupgrade.store.HttpsFalsePositivesDao
import com.duckduckgo.httpsupgrade.store.HttpsUpgradeDatabase
import com.duckduckgo.privacy.config.api.Https
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.features.https.HttpsFeature
import com.duckduckgo.privacy.config.impl.features.https.RealHttps
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.RealUnprotectedTemporary
import com.duckduckgo.privacy.config.impl.models.JsonPrivacyConfig
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.duckduckgo.privacy.config.store.HttpsExceptionEntity
import com.duckduckgo.privacy.config.store.features.https.HttpsRepository
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.UnprotectedTemporaryRepository
import com.duckduckgo.privacy.config.store.toFeatureException
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.runBlocking
import logcat.logcat
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CopyOnWriteArrayList

// FIXME reference tests forced to have visibility in things we should not have visibility like httpsupgrade-impl and impl classes :shrug:
@RunWith(Parameterized::class)
class HttpsReferenceTest(private val testCase: TestCase) {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var db: HttpsUpgradeDatabase
    private lateinit var bloomFalsePositiveDao: HttpsFalsePositivesDao
    private lateinit var bloomFilterFactory: HttpsBloomFilterFactory
    private lateinit var httpsBloomFilterSpecDao: HttpsBloomFilterSpecDao
    private lateinit var https: Https
    private lateinit var testee: HttpsUpgrader

    private var mockPixel: Pixel = mock()
    private var mockFeatureToggle: FeatureToggle = mock()
    private val mockHttpsRepository: HttpsRepository = mock()
    private val mockUnprotectedTemporaryRepository: UnprotectedTemporaryRepository = mock()
    private val mockUserAllowListRepository: UserAllowListRepository = mock()

    companion object {
        private val moshi = Moshi.Builder()
            .add(ActionJsonAdapter())
            .add(HttpsFalsePositivesJsonAdapter())
            .add(JSONObjectAdapter())
            .build()

        private val adapter: JsonAdapter<HttpsTest> = moshi.adapter(HttpsTest::class.java)

        @JvmStatic
        @Parameterized.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            var httpsTests: HttpsTest? = null
            val jsonObject: JSONObject =
                FileUtilities.getJsonObjectFromFile(HttpsReferenceTest::class.java.classLoader!!, "reference_tests/https/tests.json")

            jsonObject.keys().forEach {
                if (it == "navigations") {
                    httpsTests = adapter.fromJson(jsonObject.get(it).toString())
                }
            }
            return httpsTests?.tests?.filterNot { it.exceptPlatforms?.contains("android-browser") ?: false } ?: emptyList()
        }
    }

    data class HttpsTest(
        val name: String,
        val desc: String,
        val tests: List<TestCase>,
    )

    data class TestCase(
        val name: String,
        val siteURL: String,
        val requestURL: String,
        val requestType: String,
        val expectURL: String,
        val exceptPlatforms: List<String>?,
    )

    @UiThreadTest
    @Before
    fun setup() {
        initialiseBloomFilter()
        initialiseRemoteConfig()

        testee = HttpsUpgraderImpl(bloomFilterFactory, bloomFalsePositiveDao, toggle = mockFeatureToggle, https = https)
        testee.reloadData()
    }

    @Test
    fun whenReferenceTestRunsItReturnsTheExpectedResult() = runBlocking {
        val expectedHttps = testCase.expectURL.toUri().isHttps
        val initialUrlHttps = testCase.requestURL.toUri().isHttps

        val result = testee.shouldUpgrade(testCase.requestURL.toUri())
        if (expectedHttps && !initialUrlHttps) {
            assertTrue(result)
        } else {
            assertFalse(result)
        }
    }

    private fun initialiseRemoteConfig() {
        val httpsExceptions = mutableListOf<FeatureException>()
        val jsonAdapter: JsonAdapter<JsonPrivacyConfig> = moshi.adapter(JsonPrivacyConfig::class.java)
        val config: JsonPrivacyConfig? =
            jsonAdapter.fromJson(FileUtilities.loadText(javaClass.classLoader!!, "reference_tests/https/config_reference.json"))
        val httpsAdapter: JsonAdapter<HttpsFeature> = moshi.adapter(HttpsFeature::class.java)
        val httpsFeature: HttpsFeature? = httpsAdapter.fromJson(config?.features?.get("https").toString())

        httpsFeature?.exceptions?.map {
            httpsExceptions.add(HttpsExceptionEntity(it.domain, it.reason.orEmpty()).toFeatureException())
        }

        val isEnabled = httpsFeature?.state == "enabled"
        val exceptionsUnprotectedTemporary = CopyOnWriteArrayList(config?.unprotectedTemporary.orEmpty())

        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.HttpsFeatureName.value, isEnabled)).thenReturn(isEnabled)
        whenever(mockHttpsRepository.exceptions).thenReturn(CopyOnWriteArrayList(httpsExceptions))
        whenever(mockUnprotectedTemporaryRepository.exceptions).thenReturn(exceptionsUnprotectedTemporary)

        https = RealHttps(mockHttpsRepository, RealUnprotectedTemporary(mockUnprotectedTemporaryRepository), mockUserAllowListRepository)
    }

    private fun initialiseBloomFilter() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, HttpsUpgradeDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        bloomFalsePositiveDao = db.httpsFalsePositivesDao()
        httpsBloomFilterSpecDao = db.httpsBloomFilterSpecDao()

        val binaryDataStore = BinaryDataStore(context)
        val httpsDataPersister = HttpsDataPersister(binaryDataStore, httpsBloomFilterSpecDao, bloomFalsePositiveDao, db)

        val embeddedDataPersister = TestHttpsEmbeddedDataPersister(
            httpsDataPersister,
            binaryDataStore,
            httpsBloomFilterSpecDao,
            moshi,
        )

        bloomFilterFactory = HttpsBloomFilterFactoryImpl(
            httpsBloomFilterSpecDao,
            binaryDataStore,
            embeddedDataPersister,
            httpsDataPersister,
            mockPixel,
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
    }

    class TestHttpsEmbeddedDataPersister(
        private val httpsDataPersister: HttpsDataPersister,
        private val binaryDataStore: BinaryDataStore,
        private val httpsBloomSpecDao: HttpsBloomFilterSpecDao,
        private val moshi: Moshi,
    ) : HttpsEmbeddedDataPersister {

        override fun shouldPersistEmbeddedData(): Boolean {
            val specification = httpsBloomSpecDao.get() ?: return true
            return !binaryDataStore.verifyCheckSum(HttpsBloomFilterSpec.HTTPS_BINARY_FILE, specification.sha256)
        }

        override fun persistEmbeddedData() {
            logcat { "Updating https data from embedded files" }
            val specJson = FileUtilities.loadText(
                javaClass.classLoader!!,
                "reference_tests/https/https_bloomfilter_spec_reference.json",
            )
            val specAdapter = moshi.adapter(HttpsBloomFilterSpec::class.java)

            val falsePositivesJson = FileUtilities.loadText(
                javaClass.classLoader!!,
                "reference_tests/https/https_allowlist_reference.json",
            )
            val falsePositivesType = Types.newParameterizedType(List::class.java, HttpsFalsePositiveDomain::class.java)
            val falsePositivesAdapter: JsonAdapter<List<HttpsFalsePositiveDomain>> = moshi.adapter(falsePositivesType)

            val bytes = FileUtilities.readBytes(javaClass.classLoader!!, "reference_tests/https/https_bloomfilter_reference.bin")
            httpsDataPersister.persistBloomFilter(specAdapter.fromJson(specJson)!!, bytes, falsePositivesAdapter.fromJson(falsePositivesJson)!!)
        }
    }
}
