package com.duckduckgo.app.brokensite.api

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.pixels.AppPixelName.BROKEN_SITE_REPORT
import com.duckduckgo.app.pixels.AppPixelName.BROKEN_SITE_REPORTED
import com.duckduckgo.app.pixels.AppPixelName.PROTECTION_TOGGLE_BROKEN_SITE_REPORT
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.trackerdetection.blocklist.BlockList.Cohorts.TREATMENT
import com.duckduckgo.app.trackerdetection.blocklist.FakeFeatureTogglesInventory
import com.duckduckgo.app.trackerdetection.blocklist.TestBlockListFeature
import com.duckduckgo.app.trackerdetection.db.TdsMetadataDao
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.brokensite.api.BrokenSite
import com.duckduckgo.brokensite.api.BrokenSiteLastSentReport
import com.duckduckgo.brokensite.api.ReportFlow.DASHBOARD
import com.duckduckgo.brokensite.api.ReportFlow.MENU
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.browser.api.brokensite.BrokenSiteOpenerContext.EXTERNAL
import com.duckduckgo.browser.api.brokensite.BrokenSiteOpenerContext.NAVIGATION
import com.duckduckgo.browser.api.brokensite.BrokenSiteOpenerContext.SERP
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.FeatureName
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.api.Toggle.State.Cohort
import com.duckduckgo.feature.toggles.impl.RealFeatureTogglesInventory
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.privacy.config.api.AmpLinkInfo
import com.duckduckgo.privacy.config.api.AmpLinks
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.PrivacyConfig
import com.duckduckgo.privacy.config.api.PrivacyConfigData
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupExperimentExternalPixels
import com.squareup.moshi.Moshi
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlinx.coroutines.runBlocking
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@SuppressLint("DenyListedApi")
class BrokenSiteSubmitterTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val moshi = Moshi.Builder().build()

    private data class Config(
        val treatmentUrl: String? = null,
        val controlUrl: String? = null,
        val nextUrl: String? = null,
    )
    private val configAdapter = moshi.adapter(Config::class.java)

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

    private val networkProtectionState: NetworkProtectionState = mock()

    private val privacyProtectionsPopupExperimentExternalPixels: PrivacyProtectionsPopupExperimentExternalPixels = mock {
        runBlocking { whenever(mock.getPixelParams()).thenReturn(emptyMap()) }
    }

    private val webViewVersionProvider: WebViewVersionProvider = mock()

    private val ampLinks: AmpLinks = mock()

    private lateinit var testBlockListFeature: TestBlockListFeature
    private lateinit var inventory: FeatureTogglesInventory

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
        runBlocking { whenever(networkProtectionState.isRunning()) }.thenReturn(false)

        testBlockListFeature = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "blockList",
        ).build().create(TestBlockListFeature::class.java)

        inventory = RealFeatureTogglesInventory(
            setOf(
                FakeFeatureTogglesInventory(
                    features = listOf(
                        testBlockListFeature.tdsNextExperimentTest(),
                        testBlockListFeature.tdsNextExperimentAnotherTest(),
                    ),
                ),
            ),
            coroutineRule.testDispatcherProvider,
        )

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
            privacyProtectionsPopupExperimentExternalPixels,
            networkProtectionState,
            webViewVersionProvider,
            ampLinks,
            inventory,
        )
    }

    @Test
    fun whenVpnDisabledReportFalse() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(false)
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals("false", params["vpnOn"])
    }

    @Test
    fun whenVpnEnabledReportTrue() = runTest {
        whenever(networkProtectionState.isRunning()).thenReturn(true)
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals("true", params["vpnOn"])
    }

    @Test
    fun whenSiteInUnprotectedTemporaryThenProtectionsAreOff() {
        whenever(mockContentBlocking.isAnException(any())).thenReturn(false)
        whenever(mockUserAllowListRepository.isDomainInUserAllowList(any())).thenReturn(false)
        whenever(mockUnprotectedTemporary.isAnException(any())).thenReturn(true)
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals("false", params["protectionsState"])
    }

    @Test
    fun whenSiteInUserAllowListThenProtectionsAreOff() {
        whenever(mockContentBlocking.isAnException(any())).thenReturn(false)
        whenever(mockUserAllowListRepository.isDomainInUserAllowList(any())).thenReturn(true)
        whenever(mockUnprotectedTemporary.isAnException(any())).thenReturn(false)
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals("false", params["protectionsState"])
    }

    @Test
    fun whenSiteInContentBlockingThenProtectionsAreOff() {
        whenever(mockContentBlocking.isAnException(any())).thenReturn(true)
        whenever(mockUserAllowListRepository.isDomainInUserAllowList(any())).thenReturn(false)
        whenever(mockUnprotectedTemporary.isAnException(any())).thenReturn(false)
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(Count))
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

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals("false", params["protectionsState"])
    }

    @Test
    fun whenSiteAllowedThenProtectionsAreOn() {
        whenever(mockContentBlocking.isAnException(any())).thenReturn(false)
        whenever(mockUserAllowListRepository.isDomainInUserAllowList(any())).thenReturn(false)
        whenever(mockUnprotectedTemporary.isAnException(any())).thenReturn(false)
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals("true", params["protectionsState"])
    }

    @Test
    fun whenBrokenSiteFeedbackIsSuccessfullySubmittedThenParamSentAndSetLastSentDayIsCalledForThatDomain() = runTest {
        val lastSentDay = "2023-11-01"
        whenever(mockBrokenSiteLastSentReport.getLastSentDay(any())).thenReturn(lastSentDay)
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(Count))
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

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(Count))
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

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(Count))
        val params = paramsCaptor.firstValue

        assertFalse(params.containsKey("loginSite"))
    }

    @Test
    fun whenReportFlowIsMenuThenIncludeParam() {
        val brokenSite = getBrokenSite()
            .copy(reportFlow = MENU)

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals("menu", params["reportFlow"])
    }

    @Test
    fun whenReportFlowIsDashboardThenIncludeParam() {
        val brokenSite = getBrokenSite()
            .copy(reportFlow = DASHBOARD)

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals("dashboard", params["reportFlow"])
    }

    @Test
    fun whenReportFlowIsNullThenDoNotIncludeParam() {
        val brokenSite = getBrokenSite()
            .copy(reportFlow = null)

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.firstValue

        assertFalse("reportFlow" in params)
    }

    @Test
    fun whenPrivacyProtectionsPopupExperimentParamsArePresentThenTheyAreIncludedInPixel() = runTest {
        val params = mapOf("test_key" to "test_value")
        whenever(privacyProtectionsPopupExperimentExternalPixels.getPixelParams()).thenReturn(params)

        testee.submitBrokenSiteFeedback(getBrokenSite(), toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), any(), eq(Count))

        assertEquals("test_value", paramsCaptor.firstValue["test_key"])
    }

    @Test
    fun whenDeviceLocaleIsUSEnglishThenSendSanitizedParam() {
        val usLocale = Locale.Builder()
            .setLanguage("en")
            .setRegion("US")
            .setExtension(Locale.UNICODE_LOCALE_EXTENSION, "test")
            .build()
        whenever(mockAppBuildConfig.deviceLocale).thenReturn(usLocale)
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        val encodedParamsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), encodedParamsCaptor.capture(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals("en-US", params["locale"])
    }

    @Test
    fun whenUserRefreshCountIsZeroThenIncludeParam() {
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals("0", params["userRefreshCount"])
    }

    @Test
    fun whenUserRefreshCountIsGreaterThanZeroThenIncludeParam() {
        val brokenSite = getBrokenSite()
            .copy(userRefreshCount = 5)

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals("5", params["userRefreshCount"])
    }

    @Test
    fun whenOpenerContextIsSerpThenIncludeParam() {
        val brokenSite = getBrokenSite()
            .copy(openerContext = SERP.context)

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals("serp", params["openerContext"])
    }

    @Test
    fun whenOpenerContextIsExternalThenIncludeParam() {
        val brokenSite = getBrokenSite()
            .copy(openerContext = EXTERNAL.context)

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals("external", params["openerContext"])
    }

    @Test
    fun whenOpenerContextIsNavigationThenIncludeParam() {
        val brokenSite = getBrokenSite()
            .copy(openerContext = NAVIGATION.context)

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals("navigation", params["openerContext"])
    }

    @Test
    fun whenOpenerContextIsNullThenIncludeEmptyStringAsParam() {
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals("", params["openerContext"])
    }

    @Test
    fun whenJsPerformanceIsNullThenIncludeEmptyStringAsParam() {
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals("", params["jsPerformance"])
    }

    @Test
    fun whenJsPerformanceExistsThenIncludeParam() {
        val brokenSite = getBrokenSite()
            .copy(jsPerformance = listOf(123.45))

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals("123.45", params["jsPerformance"])
    }

    @Test
    fun whenSubmitReportThenIncludeWebViewVersion() {
        val webViewVersion = "some WebView version"
        whenever(webViewVersionProvider.getFullVersion()).thenReturn(webViewVersion)

        testee.submitBrokenSiteFeedback(getBrokenSite(), toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.firstValue

        assertEquals(webViewVersion, params["wvVersion"])
    }

    @Test
    fun whenSubmitReportThenSendBothBrokenSitePixelsButNotTogglePixel() {
        val brokenSite = getBrokenSite()
        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), any(), any(), eq(Count))

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORTED), parameters = paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.firstValue
        assertEquals(brokenSite.siteUrl, params[Pixel.PixelParameter.URL])

        verify(mockPixel, never()).fire(eq(PROTECTION_TOGGLE_BROKEN_SITE_REPORT.pixelName), any(), any(), eq(Count))
    }

    @Test
    fun whenSubmitReportAndAmpLinkIsNullThenUseSiteUrl() {
        val brokenSite = getBrokenSite()
        whenever(ampLinks.lastAmpLinkInfo).thenReturn(null)

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), parameters = paramsCaptor.capture(), any(), eq(Count))
        assertEquals(brokenSite.siteUrl, paramsCaptor.lastValue["siteUrl"])

        verify(mockPixel).fire(eq(BROKEN_SITE_REPORTED), parameters = paramsCaptor.capture(), any(), eq(Count))
        assertEquals(brokenSite.siteUrl, paramsCaptor.lastValue[Pixel.PixelParameter.URL])
    }

    @Test
    fun whenSubmitReportAndAmpLinkDoesNotMatchThenUseSiteUrl() {
        val brokenSite = getBrokenSite()
        whenever(ampLinks.lastAmpLinkInfo).thenReturn(AmpLinkInfo(ampLink = TRACKING_URL, destinationUrl = "https://someotherurl.com"))

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), parameters = paramsCaptor.capture(), any(), eq(Count))
        assertEquals(brokenSite.siteUrl, paramsCaptor.lastValue["siteUrl"])

        verify(mockPixel).fire(eq(BROKEN_SITE_REPORTED), parameters = paramsCaptor.capture(), any(), eq(Count))
        assertEquals(brokenSite.siteUrl, paramsCaptor.lastValue[Pixel.PixelParameter.URL])
    }

    @Test
    fun whenSubmitReportAndAmpLinkMatchesThenReplaceSiteUrlWithAmpLink() {
        val brokenSite = getBrokenSite()
        whenever(ampLinks.lastAmpLinkInfo).thenReturn(AmpLinkInfo(ampLink = TRACKING_URL, destinationUrl = brokenSite.siteUrl))

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), parameters = paramsCaptor.capture(), any(), eq(Count))
        assertEquals(TRACKING_URL, paramsCaptor.lastValue["siteUrl"])

        verify(mockPixel).fire(eq(BROKEN_SITE_REPORTED), parameters = paramsCaptor.capture(), any(), eq(Count))
        assertEquals(TRACKING_URL, paramsCaptor.lastValue[Pixel.PixelParameter.URL])
    }

    @Test
    fun whenSubmitReportAndBlockListExperimentActiveThenAddParameter() {
        assignToExperiment()
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite, toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), parameters = paramsCaptor.capture(), any(), eq(Count))
        assertEquals("tdsNextExperimentTest_treatment", paramsCaptor.lastValue["blockListExperiment"])
    }

    @Test
    fun whenToggleReportSendTogglePixelWithoutUnnecessaryParams() {
        val brokenSite = getBrokenSite()
        testee.submitBrokenSiteFeedback(brokenSite, toggle = true)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(PROTECTION_TOGGLE_BROKEN_SITE_REPORT.pixelName), parameters = paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.lastValue
        assertEquals(brokenSite.siteUrl, params["siteUrl"])
        assertFalse(params.containsKey("protectionsState"))
    }

    @Test
    fun whenSubmitReportAndActiveContentScopeExperimentsThenIncludeParam() = runTest {
        val brokenSite = getBrokenSite()
        val contentScopeExperiments = listOf(
            mock<Toggle>().apply {
                whenever(featureName()).thenReturn(FeatureName("test", "experiment1"))
                whenever(getCohort()).thenReturn(Cohort("control", 1, "2023-01-01T00:00:00Z"))
            },
            mock<Toggle>().apply {
                whenever(featureName()).thenReturn(FeatureName("test", "experiment2"))
                whenever(getCohort()).thenReturn(Cohort("treatment", 1, "2023-01-01T00:00:00Z"))
            },
        )

        testee.submitBrokenSiteFeedback(brokenSite.copy(contentScopeExperiments = contentScopeExperiments), toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), parameters = paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.lastValue
        assertEquals("experiment1:control,experiment2:treatment", params["contentScopeExperiments"])
    }

    @Test
    fun whenSubmitReportAndADebugFlagsThenIncludeParamSortedRemovingDuplicates() = runTest {
        val brokenSite = getBrokenSite()

        testee.submitBrokenSiteFeedback(brokenSite.copy(debugFlags = listOf("flag2", "flag1", "flag2")), toggle = false)

        val paramsCaptor = argumentCaptor<Map<String, String>>()
        verify(mockPixel).fire(eq(BROKEN_SITE_REPORT.pixelName), parameters = paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.lastValue
        assertEquals("flag1,flag2", params["debugFlags"])
    }

    private fun assignToExperiment() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        testBlockListFeature.tdsNextExperimentTest().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                settings = configAdapter.toJson(Config(treatmentUrl = "treatmentUrl", controlUrl = "controlUrl")),
                cohorts = listOf(State.Cohort(name = TREATMENT.cohortName, weight = 1, enrollmentDateET = enrollmentDateET)),
                assignedCohort = State.Cohort(name = TREATMENT.cohortName, weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
    }

    private fun getBrokenSite(): BrokenSite {
        return BrokenSite(
            category = "category",
            description = "description",
            siteUrl = "https://example.com",
            upgradeHttps = true,
            blockedTrackers = "",
            surrogates = "",
            siteType = BrokenSite.SITE_TYPE_DESKTOP,
            urlParametersRemoved = false,
            consentManaged = false,
            consentOptOutFailed = false,
            consentSelfTestFailed = false,
            errorCodes = "",
            httpErrorCodes = "",
            loginSite = null,
            reportFlow = MENU,
            userRefreshCount = 0,
            openerContext = null,
            jsPerformance = null,
            contentScopeExperiments = null,
            debugFlags = null,
        )
    }

    private companion object {
        const val TRACKING_URL = "https://foo.com"
    }
}
