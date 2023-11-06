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
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.PrivacyConfig
import com.duckduckgo.privacy.config.api.PrivacyConfigData
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.net.URLEncoder
import java.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.ParameterizedRobolectricTestRunner

@ExperimentalCoroutinesApi
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

    private val mockAppBuildConfig: AppBuildConfig = mock()

    private val mockPrivacyConfig: PrivacyConfig = mock()

    private lateinit var testee: BrokenSiteSubmitter

    companion object {
        val encodedParamsList = listOf("description", "siteUrl", "tds", "remoteConfigEtag")
        private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val adapter: JsonAdapter<ReferenceTest> = moshi.adapter(ReferenceTest::class.java)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            val referenceTest = adapter.fromJson(
                FileUtilities.loadText(
                    BrokenSitesReferenceTest::class.java.classLoader!!,
                    "reference_tests/brokensites/tests.json",
                ),
            )
            return referenceTest?.reportURL?.tests?.filterNot { it.exceptPlatforms.contains("android-browser") } ?: emptyList()
        }
    }

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        whenever(mockAppBuildConfig.deviceLocale).thenReturn(Locale.ENGLISH)
        testee = BrokenSiteSubmitter(
            mockStatisticsDataStore,
            mockVariantManager,
            mockTdsMetadataDao,
            mockGpc,
            mockFeatureToggle,
            mockPixel,
            TestScope(),
            mockAppBuildConfig,
            coroutineRule.testDispatcherProvider,
            mockPrivacyConfig,
            mock(),
            mock(),
            mock(),
            mock(),
        )
    }

    @Test
    fun whenReferenceTestRunsItReturnsTheExpectedResult() {
        whenever(mockAppBuildConfig.sdkInt).thenReturn(testCase.os?.toInt() ?: 1)
        whenever(mockAppBuildConfig.manufacturer).thenReturn(testCase.manufacturer)
        whenever(mockAppBuildConfig.model).thenReturn(testCase.model)
        whenever(mockFeatureToggle.isFeatureEnabled(any(), any())).thenReturn(true)
        whenever(mockGpc.isEnabled()).thenReturn(testCase.gpcEnabled)
        whenever(mockTdsMetadataDao.eTag()).thenReturn(testCase.blocklistVersion)
        whenever(mockStatisticsDataStore.atb).thenReturn(Atb("v123-456"))
        whenever(mockVariantManager.getVariant()).thenReturn(Variant("g", 1.0, emptyList()) { true })
        whenever(mockPrivacyConfig.privacyConfigData()).thenReturn(
            PrivacyConfigData(version = testCase.remoteConfigVersion ?: "v", eTag = testCase.remoteConfigEtag ?: "e"),
        )

        val brokenSite = BrokenSite(
            category = testCase.category,
            description = testCase.providedDescription,
            siteUrl = testCase.siteURL,
            upgradeHttps = testCase.wasUpgraded,
            blockedTrackers = testCase.blockedTrackers.joinToString(","),
            surrogates = testCase.surrogates.joinToString(","),
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.DESKTOP_SITE,
            urlParametersRemoved = testCase.urlParametersRemoved.toBoolean(),
            consentManaged = testCase.consentManaged.toBoolean(),
            consentOptOutFailed = testCase.consentOptOutFailed.toBoolean(),
            consentSelfTestFailed = testCase.consentSelfTestFailed.toBoolean(),
            errorCodes = "",
            httpErrorCodes = "",
            loginSite = null,
        )

        testee.submitBrokenSiteFeedback(brokenSite)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(AppPixelName.BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture())

        val params = paramsCaptor.firstValue
        val encodedParams = encodedParamsCaptor.firstValue

        testCase.expectReportURLParams.forEach { param ->
            if (encodedParams.contains(param.name)) {
                assertTrue(encodedParams.containsKey(param.name))
                assertEquals(param.value, encodedParams[param.name])
            } else {
                val result = if (encodedParamsList.contains(param.name)) {
                    URLEncoder.encode(params[param.name], "UTF-8")
                } else {
                    params[param.name]
                }
                assertTrue(params.containsKey(param.name))
                assertEquals("${param.name} failed", param.value, result)
            }
        }
    }

    data class TestCase(
        val name: String,
        val siteURL: String,
        val wasUpgraded: Boolean,
        val category: String,
        val providedDescription: String?,
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
        val exceptPlatforms: List<String>,
        val urlParametersRemoved: String,
        val consentManaged: String,
        val consentOptOutFailed: String,
        val consentSelfTestFailed: String,
        val remoteConfigEtag: String?,
        val remoteConfigVersion: String?,
    )

    data class UrlParam(
        val name: String,
        val value: String,
    )

    data class BrokenSiteTest(
        val name: String,
        val tests: List<TestCase>,
    )

    data class ReferenceTest(
        val reportURL: BrokenSiteTest,
    )
}
