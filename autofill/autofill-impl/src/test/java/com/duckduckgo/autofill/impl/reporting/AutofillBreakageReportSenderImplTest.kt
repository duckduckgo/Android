package com.duckduckgo.autofill.impl.reporting

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.AutofillGlobalCapabilityChecker
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SITE_BREAKAGE_REPORT
import com.duckduckgo.autofill.impl.store.NeverSavedSiteRepository
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
import java.util.*

@RunWith(AndroidJUnit4::class)
class AutofillBreakageReportSenderImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val appBuildConfig: AppBuildConfig = mock()
    private val autoCapabilityChecker: AutofillGlobalCapabilityChecker = mock()
    private val emailManager: EmailManager = mock()
    private val neverSavedSiteRepository: NeverSavedSiteRepository = mock()
    private val pixel: Pixel = mock()
    private val paramsCaptor = argumentCaptor<Map<String, String>>()

    private val testee = AutofillBreakageReportSenderImpl(
        pixel = pixel,
        neverSavedSiteRepository = neverSavedSiteRepository,
        emailManager = emailManager,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        appCoroutineScope = coroutineTestRule.testScope,
        autofillCapabilityChecker = autoCapabilityChecker,
        appBuildConfig = appBuildConfig,
    )

    @Before
    fun setup() = runTest {
        configureDefaults()
    }

    @Test
    fun whenReportSentThenPixelFired() = runTest {
        sendReport()
        verifyPixelSentHasCorrectNumberOfParameters()
    }

    @Test
    fun whenReportSentThenCorrectLanguageIncludedPixelFired() = runTest {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.FRANCE)
        sendReport()
        "fr".assertMatchesLanguage()
    }

    @Test
    fun whenReportSentWithEmailProtectionEnabledThenCorrectParamIncluded() = runTest {
        whenever(emailManager.isSignedIn()).thenReturn(true)
        sendReport()
        "true".assertMatchesEmailProtection()
    }

    @Test
    fun whenReportSentWithEmailProtectionDisabledThenCorrectParamIncluded() = runTest {
        whenever(emailManager.isSignedIn()).thenReturn(false)
        sendReport()
        "false".assertMatchesEmailProtection()
    }

    @Test
    fun whenReportSentWithAutofillEnabledThenCorrectParamIncluded() = runTest {
        whenever(autoCapabilityChecker.isAutofillEnabledByUser()).thenReturn(true)
        sendReport()
        "true".assertMatchesAutofillEnabled()
    }

    @Test
    fun whenReportSentWithAutofillDisabledThenCorrectParamIncluded() = runTest {
        whenever(autoCapabilityChecker.isAutofillEnabledByUser()).thenReturn(false)
        sendReport()
        "false".assertMatchesAutofillEnabled()
    }

    @Test
    fun whenReportSentWithPrivacyProtectionEnabledThenCorrectParamIncluded() = runTest {
        sendReport(protectionStatus = true)
        "true".assertMatchesPrivacyProtectionStatus()
    }

    @Test
    fun whenReportSentWithPrivacyProtectionDisabledThenCorrectParamIncluded() = runTest {
        sendReport(protectionStatus = false)
        "false".assertMatchesPrivacyProtectionStatus()
    }

    @Test
    fun whenReportSentWithPrivacyProtectionUnknownThenCorrectParamIncluded() = runTest {
        sendReport(protectionStatus = null)
        "unknown".assertMatchesPrivacyProtectionStatus()
    }

    @Test
    fun whenReportSentWithNeverSavedSiteTrueThenCorrectParamIncluded() = runTest {
        whenever(neverSavedSiteRepository.isInNeverSaveList(any())).thenReturn(true)
        sendReport()
        "true".assertMatchesNeverSavedSite()
    }

    @Test
    fun whenReportSentWithNeverSavedSiteFalseThenCorrectParamIncluded() = runTest {
        whenever(neverSavedSiteRepository.isInNeverSaveList(any())).thenReturn(false)
        sendReport()
        "false".assertMatchesNeverSavedSite()
    }

    @Test
    fun whenUrlContainsQueryParamsReportSentWithCorrectUrl() {
        sendReport(url = "https://example.com?q=hello")
        "https://example.com".assertMatchesWebsite()
    }

    @Test
    fun whenUrlContainsPortSentWithCorrectUrl() {
        sendReport(url = "https://example.com:8080")
        "https://example.com%3A8080".assertMatchesWebsite()
    }

    @Test
    fun whenUrlContainsPathSentWithCorrectUrl() {
        sendReport(url = "https://example.com/foo/bar")
        "https://example.com/foo/bar".assertMatchesWebsite()
    }

    @Test
    fun whenUrlContainsFragmentSentWithCorrectUrl() {
        sendReport(url = "https://example.com/foo#bar")
        "https://example.com/foo#bar".assertMatchesWebsite()
    }

    private suspend fun configureDefaults() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.US)
        whenever(autoCapabilityChecker.isAutofillEnabledByUser()).thenReturn(true)
        whenever(emailManager.isSignedIn()).thenReturn(true)
        whenever(neverSavedSiteRepository.isInNeverSaveList(any())).thenReturn(true)
    }

    private fun verifyPixelSentHasCorrectNumberOfParameters() {
        assertEquals(6, paramsCaptor.lastValue.size)
    }

    private fun sendReport(url: String = "https://example.com", protectionStatus: Boolean? = true) {
        testee.sendBreakageReport(url, protectionStatus)
        verify(pixel).fire(eq(AUTOFILL_SITE_BREAKAGE_REPORT), paramsCaptor.capture(), any(), eq(Count))
    }

    private fun String.assertMatchesEmailProtection() {
        assertEquals(this, paramsCaptor.lastValue["email_protection"])
    }

    private fun String.assertMatchesAutofillEnabled() {
        assertEquals(this, paramsCaptor.lastValue["autofill_enabled"])
    }

    private fun String.assertMatchesPrivacyProtectionStatus() {
        assertEquals(this, paramsCaptor.lastValue["privacy_protection"])
    }

    private fun String.assertMatchesLanguage() {
        assertEquals(this, paramsCaptor.lastValue["language"])
    }

    private fun String.assertMatchesNeverSavedSite() {
        assertEquals(this, paramsCaptor.lastValue["never_prompt"])
    }

    private fun String.assertMatchesWebsite() {
        assertEquals(this, paramsCaptor.lastValue["website"])
    }
}
