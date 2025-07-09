/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.malicioussiteprotection.impl

import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.IsMaliciousResult.ConfirmedResult
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.MaliciousStatus
import com.duckduckgo.malicioussiteprotection.impl.data.RealMaliciousSiteRepository
import com.duckduckgo.malicioussiteprotection.impl.data.db.MaliciousSiteDao
import com.duckduckgo.malicioussiteprotection.impl.data.db.MaliciousSitesDatabase
import com.duckduckgo.malicioussiteprotection.impl.data.network.FilterResponse
import com.duckduckgo.malicioussiteprotection.impl.data.network.FilterSetResponse
import com.duckduckgo.malicioussiteprotection.impl.data.network.HashPrefixResponse
import com.duckduckgo.malicioussiteprotection.impl.data.network.MaliciousSiteDatasetService
import com.duckduckgo.malicioussiteprotection.impl.data.network.MaliciousSiteService
import com.duckduckgo.malicioussiteprotection.impl.data.network.RevisionResponse
import com.duckduckgo.malicioussiteprotection.impl.domain.RealMaliciousSiteProtection
import com.duckduckgo.malicioussiteprotection.impl.domain.RealUrlCanonicalization
import com.duckduckgo.malicioussiteprotection.impl.domain.UrlCanonicalization
import com.duckduckgo.malicioussiteprotection.impl.remoteconfig.MaliciousSiteProtectionRCRepository
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.security.MessageDigest
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
class MaliciousSiteProtectionReferenceTest(private val testCase: TestCase) {

    companion object {
        private val moshi = Moshi.Builder().build()
        private val adapter: JsonAdapter<ReferenceTest> = moshi.adapter(ReferenceTest::class.java)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            val test = adapter.fromJson(
                FileUtilities.loadText(
                    MaliciousSiteProtectionReferenceTest::class.java.classLoader!!,
                    "reference_tests/block/block_tests.json",
                ),
            )
            val phishingDetectionTests = test?.phishingDetectionTests?.tests ?: emptyList()
            val malwareDetectionTests = test?.malwareDetectionTests?.tests ?: emptyList()
            return (phishingDetectionTests + malwareDetectionTests).filterNot { it.exceptPlatforms.contains("android-browser") }
        }
    }

    private lateinit var testee: RealMaliciousSiteProtection

    @get:org.junit.Rule
    var coroutineRule = com.duckduckgo.common.test.CoroutineTestRule()

    private val maliciousSiteDao: MaliciousSiteDao = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        MaliciousSitesDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build().maliciousSiteDao()
    private val maliciousSiteService: MaliciousSiteService = mock()
    private val maliciousSiteDatasetService: MaliciousSiteDatasetService = mock()
    private val mockPixel: Pixel = mock()
    private val moshi = Moshi.Builder().build()
    private val mockMaliciousSiteProtectionRCRepository: MaliciousSiteProtectionRCRepository = mock()
    private val mockMaliciousSiteProtectionRCFeature: MaliciousSiteProtectionRCFeature = mock()
    private val urlCanonicalization: UrlCanonicalization = RealUrlCanonicalization(
        mockMaliciousSiteProtectionRCFeature,
    )
    private val messageDigest: MessageDigest = MessageDigest.getInstance("SHA-256")
    private val repository = RealMaliciousSiteRepository(
        maliciousSiteDao,
        maliciousSiteService,
        maliciousSiteDatasetService,
        coroutineRule.testDispatcherProvider,
        mockPixel,
    )

    @Before
    fun setup() = runBlocking {
        val filterSetJsonAdapter: JsonAdapter<Set<FilterResponse>> = moshi.adapter(
            Types.newParameterizedType(Set::class.java, FilterResponse::class.java),
        )
        val hashPrefixesJsonAdapter: JsonAdapter<Set<String>> = moshi.adapter(Types.newParameterizedType(Set::class.java, String::class.java))
        val phishingFilterSetJson = FileUtilities.loadText(
            MaliciousSiteProtectionReferenceTest::class.java.classLoader!!,
            "reference_tests/block/reference_phishing_filterSet.json",
        )
        val malwareFilterSetJson = FileUtilities.loadText(
            MaliciousSiteProtectionReferenceTest::class.java.classLoader!!,
            "reference_tests/block/reference_malware_filterSet.json",
        )
        val phishingHashPrefixesJson = FileUtilities.loadText(
            MaliciousSiteProtectionReferenceTest::class.java.classLoader!!,
            "reference_tests/block/reference_phishing_hashPrefixes.json",
        )
        val malwareHashPrefixesJson = FileUtilities.loadText(
            MaliciousSiteProtectionReferenceTest::class.java.classLoader!!,
            "reference_tests/block/reference_malware_hashPrefixes.json",
        )
        whenever(maliciousSiteService.getRevision()).thenReturn(RevisionResponse(1))
        whenever(maliciousSiteDatasetService.getPhishingFilterSet(any())).thenReturn(
            FilterSetResponse(insert = filterSetJsonAdapter.fromJson(phishingFilterSetJson)!!, delete = emptySet(), revision = 1, replace = true),
        )
        whenever(maliciousSiteDatasetService.getMalwareFilterSet(any())).thenReturn(
            FilterSetResponse(insert = filterSetJsonAdapter.fromJson(malwareFilterSetJson)!!, delete = emptySet(), revision = 1, replace = true),
        )
        whenever(maliciousSiteDatasetService.getPhishingHashPrefixes(any())).thenReturn(
            HashPrefixResponse(
                insert = hashPrefixesJsonAdapter.fromJson(phishingHashPrefixesJson)!!,
                delete = emptySet(),
                revision = 1,
                replace = true,
            ),
        )
        whenever(maliciousSiteDatasetService.getMalwareHashPrefixes(any())).thenReturn(
            HashPrefixResponse(
                insert = hashPrefixesJsonAdapter.fromJson(malwareHashPrefixesJson)!!,
                delete = emptySet(),
                revision = 1,
                replace = true,
            ),
        )
        whenever(mockMaliciousSiteProtectionRCFeature.isFeatureEnabled()).thenReturn(true)
        whenever(mockMaliciousSiteProtectionRCFeature.stripWWWPrefix()).thenReturn(true)
        repository.loadFilters(*enumValues<Feed>())
        repository.loadHashPrefixes(*enumValues<Feed>())

        testee = RealMaliciousSiteProtection(
            coroutineRule.testDispatcherProvider,
            coroutineRule.testScope,
            repository,
            mockMaliciousSiteProtectionRCRepository,
            messageDigest,
            mockMaliciousSiteProtectionRCFeature,
            urlCanonicalization,
        )
    }

    @Test
    fun whenReferenceTestRunsItReturnsTheExpectedResult() = runTest {
        testCase.exceptPlatforms
        val maliciousResult = testee.isMalicious(testCase.siteURL.toUri()) { it: MaliciousStatus ->
            if (it is MaliciousStatus.Malicious) {
                assertEquals(testCase.expectBlock, true)
            } else {
                assertEquals(testCase.expectBlock, false)
            }
        }

        if (maliciousResult is ConfirmedResult) {
            assertEquals(testCase.expectBlock, maliciousResult.status is MaliciousStatus.Malicious)
        }
    }

    data class TestCase(
        val name: String,
        val siteURL: String,
        val expectBlock: Boolean,
        val exceptPlatforms: List<String>,
    )

    data class MaliciousSiteDetectionTest(
        val name: String,
        val desc: String,
        val tests: List<TestCase>,
    )

    data class ReferenceTest(
        val phishingDetectionTests: MaliciousSiteDetectionTest,
        val malwareDetectionTests: MaliciousSiteDetectionTest,
    )
}
