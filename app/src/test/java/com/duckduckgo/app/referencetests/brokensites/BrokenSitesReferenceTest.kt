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

package com.duckduckgo.app.referencetests.brokensites

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.FileUtilities
import com.duckduckgo.app.brokensite.BrokenSiteViewModel
import com.duckduckgo.app.brokensite.api.BrokenSiteSubmitter
import com.duckduckgo.app.brokensite.model.BrokenSite
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.trackerdetection.db.TdsMetadataDao
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.robolectric.ParameterizedRobolectricTestRunner
import java.net.URLEncoder

@RunWith(ParameterizedRobolectricTestRunner::class)
class BrokenSitesReferenceTest(private val testCase: TestCase) {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockPixel: Pixel = mock()

    private val mockVariantManager: VariantManager = mock()

    private val mockTdsMetadataDao: TdsMetadataDao = mock()

    private val mockGpc: Gpc = mock()

    private val mockFeatureToggle: FeatureToggle = mock()

    private val mockStatisticsDataStore: StatisticsDataStore = mock()

    private lateinit var testee: BrokenSiteSubmitter

    companion object {
        val encodedParams = listOf("surrogates", "blockedTrackers")
        val onlyExistParams = listOf("manufacturer", "os", "model")
        private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val adapter: JsonAdapter<ReferenceTest> = moshi.adapter(ReferenceTest::class.java)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            val referenceTest = adapter.fromJson(FileUtilities.loadText(BrokenSitesReferenceTest::class.java.classLoader!!, "reference_tests/brokensites/tests.json"))
            return referenceTest?.reportURL?.tests?.filterNot { it.exceptPlatforms.contains("android-browser") } ?: emptyList()
        }
    }

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        testee = BrokenSiteSubmitter(mockStatisticsDataStore, mockVariantManager, mockTdsMetadataDao, mockGpc, mockFeatureToggle, mockPixel, TestScope(), coroutineRule.testDispatcherProvider)
    }

    @After
    fun after() {
    }

    @Test
    fun whenCanSubmitBrokenSiteAndUrlNotNullAndSubmitPressedThenReportAndPixelSubmitted() {
        whenever(mockFeatureToggle.isFeatureEnabled(any(), any())).thenReturn(true)
        whenever(mockGpc.isEnabled()).thenReturn(testCase.gpcEnabled)
        whenever(mockTdsMetadataDao.eTag()).thenReturn(testCase.blocklistVersion)
        whenever(mockStatisticsDataStore.atb).thenReturn(Atb("v123-456"))
        whenever(mockVariantManager.getVariant()).thenReturn(Variant("g", 1.0, emptyList()) { true })

        val brokenSite = BrokenSite(
            category = testCase.category,
            siteUrl = testCase.siteURL,
            upgradeHttps = testCase.wasUpgraded,
            blockedTrackers = testCase.blockedTrackers.joinToString(","),
            surrogates = testCase.surrogates.joinToString(","),
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.DESKTOP_SITE
        )

        testee.submitBrokenSiteFeedback(brokenSite)

        val expectedParamsCaptor = argumentCaptor<Map<String, String>>()
        val expectedEncodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(AppPixelName.BROKEN_SITE_REPORT.pixelName), expectedParamsCaptor.capture(), expectedEncodedParamsCaptor.capture())

        val expectedParams = expectedParamsCaptor.firstValue
        val expectedEncodedParams = expectedEncodedParamsCaptor.firstValue

        testCase.expectReportURLParams.forEach { param ->
            if (encodedParams.contains(param.name)) {
                assertTrue(expectedEncodedParams.containsKey(param.name))
                assertEquals(param.value, expectedEncodedParams[param.name])
            }
            else {
                assertTrue(expectedParams.containsKey(param.name))
                if (!onlyExistParams.contains(param.name)) {
                    assertEquals(param.value, URLEncoder.encode(expectedParams[param.name], "UTF-8"))
                }
            }
        }
    }

    data class TestCase(
        val name: String,
        val siteURL: String,
        val wasUpgraded: Boolean,
        val category: String,
        val blockedTrackers: List<String>,
        val surrogates: List<String>,
        val atb: String,
        val blocklistVersion: String,
        val manufacturer: String?,
        val model: String?,
        val os: String?,
        val gpcEnabled: Boolean = false,
        val expectReportURLPrefix: String,
        val expectReportURLParams: List<UrlParam>,
        val exceptPlatforms: List<String>
    )

    data class UrlParam(val name: String, val value: String)

    data class BrokenSiteTest(
        val name: String,
        val tests: List<TestCase>
    )

    data class ReferenceTest(
        val reportURL: BrokenSiteTest
    )
}
