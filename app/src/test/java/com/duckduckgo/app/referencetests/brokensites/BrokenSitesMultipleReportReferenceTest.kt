/*
 * Copyright (c) 2023 DuckDuckGo
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

import com.duckduckgo.app.brokensite.BrokenSiteViewModel
import com.duckduckgo.app.brokensite.api.BrokenSiteSubmitter
import com.duckduckgo.app.brokensite.model.BrokenSite
import com.duckduckgo.app.brokensite.model.ReportFlow
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.COUNT
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.trackerdetection.db.TdsMetadataDao
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.brokensite.api.BrokenSiteLastSentReport
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.PrivacyConfig
import com.duckduckgo.privacy.config.api.PrivacyConfigData
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupExperimentPixelParamsProvider
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.net.URLEncoder
import java.util.*
import java.util.regex.Pattern
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
class BrokenSitesMultipleReportReferenceTest(private val testCase: MultipleReportTestCase) {

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

    private val mockBrokenSiteLastSentReport: BrokenSiteLastSentReport = mock()

    private val privacyProtectionsPopupExperimentPixelParamsProvider =
        object : PrivacyProtectionsPopupExperimentPixelParamsProvider {
            override suspend fun getPixelParams(): Map<String, String> = emptyMap()
        }

    private lateinit var testee: BrokenSiteSubmitter

    companion object {
        val encodedParamsList = listOf("description", "siteUrl", "tds", "remoteConfigEtag")
        private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val adapter: JsonAdapter<ReferenceTest> = moshi.adapter(ReferenceTest::class.java)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<MultipleReportTestCase> {
            val referenceTest = adapter.fromJson(
                FileUtilities.loadText(
                    BrokenSitesMultipleReportReferenceTest::class.java.classLoader!!,
                    "reference_tests/brokensites/multiple_report_tests.json",
                ),
            )
            return referenceTest?.reportURL?.tests?.filterNot {
                it.exceptPlatforms.contains("android-browser")
            } ?: emptyList()
        }
    }

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
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
            mockBrokenSiteLastSentReport,
            privacyProtectionsPopupExperimentPixelParamsProvider,
        )
    }

    @Test
    fun whenReferenceTestRunsItReturnsTheExpectedResult() = runTest {
        var previousSite: String?
        var currentSite: String? = null
        testCase.reports.forEach { report ->
            previousSite = currentSite
            currentSite = report.siteURL

            whenever(mockAppBuildConfig.sdkInt).thenReturn(report.os?.toInt() ?: 1)
            whenever(mockAppBuildConfig.manufacturer).thenReturn(report.manufacturer)
            whenever(mockAppBuildConfig.model).thenReturn(report.model)
            whenever(mockAppBuildConfig.deviceLocale).thenReturn(Locale.US)
            whenever(mockFeatureToggle.isFeatureEnabled(any(), any())).thenReturn(true)
            whenever(mockGpc.isEnabled()).thenReturn(report.gpcEnabled)
            whenever(mockTdsMetadataDao.eTag()).thenReturn(report.blocklistVersion)
            whenever(mockStatisticsDataStore.atb).thenReturn(Atb("v123-456"))
            whenever(mockVariantManager.getVariantKey()).thenReturn("g")
            whenever(mockPrivacyConfig.privacyConfigData()).thenReturn(
                PrivacyConfigData(version = report.remoteConfigVersion ?: "v", eTag = report.remoteConfigEtag ?: "e"),
            )

            if (previousSite == currentSite) {
                whenever(mockBrokenSiteLastSentReport.getLastSentDay(any())).thenReturn("2023-11-01")
            } else {
                whenever(mockBrokenSiteLastSentReport.getLastSentDay(any())).thenReturn(null)
            }

            val brokenSite = BrokenSite(
                category = report.category,
                description = report.providedDescription,
                siteUrl = report.siteURL,
                upgradeHttps = report.wasUpgraded,
                blockedTrackers = report.blockedTrackers.joinToString(","),
                surrogates = report.surrogates.joinToString(","),
                webViewVersion = "webViewVersion",
                siteType = BrokenSiteViewModel.DESKTOP_SITE,
                urlParametersRemoved = report.urlParametersRemoved.toBoolean(),
                consentManaged = report.consentManaged.toBoolean(),
                consentOptOutFailed = report.consentOptOutFailed.toBoolean(),
                consentSelfTestFailed = report.consentSelfTestFailed.toBoolean(),
                errorCodes = "",
                httpErrorCodes = "",
                loginSite = null,
                reportFlow = ReportFlow.MENU,
            )

            testee.submitBrokenSiteFeedback(brokenSite)

            val paramsCaptor = argumentCaptor<Map<String, String>>()
            val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
            verify(mockPixel).fire(eq(AppPixelName.BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(COUNT))

            val params = paramsCaptor.firstValue
            val encodedParams = encodedParamsCaptor.firstValue

            report.expectReportURLParams.forEach { param ->
                if (encodedParams.contains(param.name)) {
                    assertTrue(encodedParams.containsKey(param.name))
                    assertEquals(param.value, encodedParams[param.name])
                } else {
                    val result = when {
                        encodedParamsList.contains(param.name) -> URLEncoder.encode(params[param.name], "UTF-8")
                        param.present != false -> params[param.name]
                        else -> null
                    }
                    if (result == null) {
                        assertFalse(params.containsKey(param.name))
                        assertNull("${param.name} failed", param.value)
                    } else {
                        assertTrue(params.containsKey(param.name))
                        if (param.matches != null) {
                            val pattern = Pattern.compile(param.matches)
                            assertTrue("${param.name} failed to match pattern", pattern.matcher(result).matches())
                        } else {
                            assertEquals("${param.name} failed", param.value, result)
                        }
                    }
                }
            }
            reset(mockPixel)
            reset(mockBrokenSiteLastSentReport)
        }
    }

    data class MultipleReportTestCase(
        val name: String,
        val reports: List<Report>,
        val exceptPlatforms: List<String>,
    )

    data class Report(
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
        val urlParametersRemoved: String,
        val consentManaged: String,
        val consentOptOutFailed: String,
        val consentSelfTestFailed: String,
        val remoteConfigEtag: String?,
        val remoteConfigVersion: String?,
        val lastSentDay: String?,
    )

    data class UrlParam(
        val name: String,
        val value: String,
        val present: Boolean?,
        val matches: String?,
    )

    data class BrokenSiteTest(
        val name: String,
        val tests: List<MultipleReportTestCase>,
    )

    data class ReferenceTest(
        val reportURL: BrokenSiteTest,
    )
}
