package com.duckduckgo.app.brokensite.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.brokensite.BrokenSiteViewModel
import com.duckduckgo.app.brokensite.model.BrokenSite
import com.duckduckgo.app.brokensite.model.ReportFlow
import com.duckduckgo.app.brokensite.model.ReportFlow.DASHBOARD
import com.duckduckgo.app.brokensite.model.ReportFlow.MENU
import com.duckduckgo.app.pixels.AppPixelName.BROKEN_SITE_REPORT
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.COUNT
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.trackerdetection.db.TdsMetadataDao
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.brokensite.api.BrokenSiteLastSentReport
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.PrivacyConfig
import com.duckduckgo.privacy.config.api.PrivacyConfigData
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupExperimentPixelParamsProvider
import java.util.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class BrokenSiteSubmitterTest {
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

    private val mockUserAllowListRepository: UserAllowListRepository = mock()

    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()

    private val mockContentBlocking: ContentBlocking = mock()

    private val mockBrokenSiteLastSentReport: BrokenSiteLastSentReport = mock()

    private val privacyProtectionsPopupExperimentPixelParamsProvider = FakePrivacyProtectionsPopupExperimentPixelParamsProvider()

    private lateinit var testee: BrokenSiteSubmitter

    @Before
    fun before() {
        whenever(mockAppBuildConfig.deviceLocale).thenReturn(Locale.ENGLISH)
        whenever(mockAppBuildConfig.sdkInt).thenReturn(1)
        whenever(mockAppBuildConfig.manufacturer).thenReturn("manufacturer")
        whenever(mockAppBuildConfig.model).thenReturn("model")
        whenever(mockFeatureToggle.isFeatureEnabled(any(), any())).thenReturn(true)
        whenever(mockGpc.isEnabled()).thenReturn(true)
        whenever(mockTdsMetadataDao.eTag()).thenReturn("eTAG")
        whenever(mockStatisticsDataStore.atb).thenReturn(Atb("v123-456"))
        whenever(mockVariantManager.getVariantKey()).thenReturn("g")
        whenever(mockPrivacyConfig.privacyConfigData()).thenReturn(PrivacyConfigData(version = "v", eTag = "e"))

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
            mockUserAllowListRepository,
            mockUnprotectedTemporary,
            mockContentBlocking,
            mockBrokenSiteLastSentReport,
            privacyProtectionsPopupExperimentPixelParamsProvider,
        )
    }

    @Test
    fun whenSiteInUnprotectedTemporaryThenProtectionsAreOff() {
        whenever(mockContentBlocking.isAnException(any())).thenReturn(false)
        whenever(mockUserAllowListRepository.isDomainInUserAllowList(any())).thenReturn(false)
        whenever(mockUnprotectedTemporary.isAnException(any())).thenReturn(true)
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(COUNT))
        val params = paramsCaptor.firstValue

        assertEquals("false", params["protectionsState"])
    }

    @Test
    fun whenSiteInUserAllowListThenProtectionsAreOff() {
        whenever(mockContentBlocking.isAnException(any())).thenReturn(false)
        whenever(mockUserAllowListRepository.isDomainInUserAllowList(any())).thenReturn(true)
        whenever(mockUnprotectedTemporary.isAnException(any())).thenReturn(false)
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(COUNT))
        val params = paramsCaptor.firstValue

        assertEquals("false", params["protectionsState"])
    }

    @Test
    fun whenSiteInContentBlockingThenProtectionsAreOff() {
        whenever(mockContentBlocking.isAnException(any())).thenReturn(true)
        whenever(mockUserAllowListRepository.isDomainInUserAllowList(any())).thenReturn(false)
        whenever(mockUnprotectedTemporary.isAnException(any())).thenReturn(false)
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(COUNT))
        val params = paramsCaptor.firstValue

        assertEquals("false", params["protectionsState"])
    }

    @Test
    fun whenSiteInContentBlockingDisabledThenProtectionsAreOff() {
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.ContentBlockingFeatureName.value, true)).thenReturn(false)

        whenever(mockContentBlocking.isAnException(any())).thenReturn(true)
        whenever(mockUserAllowListRepository.isDomainInUserAllowList(any())).thenReturn(true)
        whenever(mockUnprotectedTemporary.isAnException(any())).thenReturn(true)
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(COUNT))
        val params = paramsCaptor.firstValue

        assertEquals("false", params["protectionsState"])
    }

    @Test
    fun whenSiteAllowedThenProtectionsAreOn() {
        whenever(mockContentBlocking.isAnException(any())).thenReturn(false)
        whenever(mockUserAllowListRepository.isDomainInUserAllowList(any())).thenReturn(false)
        whenever(mockUnprotectedTemporary.isAnException(any())).thenReturn(false)
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(COUNT))
        val params = paramsCaptor.firstValue

        assertEquals("true", params["protectionsState"])
    }

    @Test
    fun whenBrokenSiteFeedbackIsSuccessfullySubmittedThenParamSentAndSetLastSentDayIsCalledForThatDomain() = runTest {
        val lastSentDay = "2023-11-01"
        whenever(mockBrokenSiteLastSentReport.getLastSentDay(any())).thenReturn(lastSentDay)
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(COUNT))
        val params = paramsCaptor.firstValue

        assertEquals(lastSentDay, params["lastSentDay"])
        verify(mockBrokenSiteLastSentReport).setLastSentDay("example.com")
    }

    @Test
    fun whenDeviceIsEnglishThenIncludeLoginSite() {
        whenever(mockAppBuildConfig.deviceLocale).thenReturn(Locale.ENGLISH)
        whenever(mockContentBlocking.isAnException(any())).thenReturn(false)
        whenever(mockUserAllowListRepository.isDomainInUserAllowList(any())).thenReturn(false)
        whenever(mockUnprotectedTemporary.isAnException(any())).thenReturn(false)
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(COUNT))
        val params = paramsCaptor.firstValue

        assertEquals("", params["loginSite"])
    }

    @Test
    fun whenDeviceIsNotEnglishThenDoNotIncludeLoginSite() {
        whenever(mockAppBuildConfig.deviceLocale).thenReturn(Locale.FRANCE)
        whenever(mockContentBlocking.isAnException(any())).thenReturn(false)
        whenever(mockUserAllowListRepository.isDomainInUserAllowList(any())).thenReturn(false)
        whenever(mockUnprotectedTemporary.isAnException(any())).thenReturn(false)
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(COUNT))
        val params = paramsCaptor.firstValue

        assertFalse(params.containsKey("loginSite"))
    }

    @Test
    fun whenReportFlowIsMenuThenIncludeParam() {
        val brokenSite = getBrokenSite()
            .copy(reportFlow = MENU)

        testee.submitBrokenSiteFeedback(brokenSite)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), any(), eq(COUNT))
        val params = paramsCaptor.firstValue

        assertEquals("menu", params["reportFlow"])
    }

    @Test
    fun whenReportFlowIsDashboardThenIncludeParam() {
        val brokenSite = getBrokenSite()
            .copy(reportFlow = DASHBOARD)

        testee.submitBrokenSiteFeedback(brokenSite)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), any(), eq(COUNT))
        val params = paramsCaptor.firstValue

        assertEquals("dashboard", params["reportFlow"])
    }

    @Test
    fun whenReportFlowIsNullThenDoNotIncludeParam() {
        val brokenSite = getBrokenSite()
            .copy(reportFlow = null)

        testee.submitBrokenSiteFeedback(brokenSite)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), any(), eq(COUNT))
        val params = paramsCaptor.firstValue

        assertFalse("reportFlow" in params)
    }

    @Test
    fun whenPrivacyProtectionsPopupExperimentParamsArePresentThenTheyAreIncludedInPixel() = runTest {
        val params = mapOf("test_key" to "test_value")
        privacyProtectionsPopupExperimentPixelParamsProvider.params = params

        testee.submitBrokenSiteFeedback(getBrokenSite())

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), any(), eq(COUNT))

        assertEquals("test_value", paramsCaptor.firstValue["test_key"])
    }

    private fun getBrokenSite(): BrokenSite {
        return BrokenSite(
            category = "category",
            description = "description",
            siteUrl = "https://example.com",
            upgradeHttps = true,
            blockedTrackers = "",
            surrogates = "",
            webViewVersion = "webViewVersion",
            siteType = BrokenSiteViewModel.DESKTOP_SITE,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = "",
            httpErrorCodes = "",
            loginSite = null,
            reportFlow = ReportFlow.MENU,
        )
    }
}

private class FakePrivacyProtectionsPopupExperimentPixelParamsProvider : PrivacyProtectionsPopupExperimentPixelParamsProvider {
    var params: Map<String, String> = emptyMap()

    override suspend fun getPixelParams(): Map<String, String> = params
}
