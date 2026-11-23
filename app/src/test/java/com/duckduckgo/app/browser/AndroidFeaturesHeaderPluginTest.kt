package com.duckduckgo.app.browser

import com.duckduckgo.app.browser.trafficquality.AndroidFeaturesHeaderPlugin
import com.duckduckgo.app.browser.trafficquality.AndroidFeaturesHeaderPlugin.Companion.X_DUCKDUCKGO_ANDROID_APP_VERSION_HEADER
import com.duckduckgo.app.browser.trafficquality.AndroidFeaturesHeaderPlugin.Companion.X_DUCKDUCKGO_ANDROID_HEADER
import com.duckduckgo.app.browser.trafficquality.AppVersionHeaderProvider
import com.duckduckgo.app.browser.trafficquality.CustomHeaderAllowedChecker
import com.duckduckgo.app.browser.trafficquality.Result.Allowed
import com.duckduckgo.app.browser.trafficquality.Result.NotAllowed
import com.duckduckgo.app.browser.trafficquality.configEnabledForCurrentVersion
import com.duckduckgo.app.browser.trafficquality.remote.AndroidFeaturesHeaderProvider
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AndroidFeaturesHeaderPluginTest {

    private lateinit var testee: AndroidFeaturesHeaderPlugin

    private val mockDuckDuckGoUrlDetector: DuckDuckGoUrlDetector = mock()
    private val mockAndroidBrowserConfigFeature: AndroidBrowserConfigFeature = mock()
    private val mockEnabledToggle: Toggle = mock { on { it.isEnabled() } doReturn true }
    private val mockDisabledToggle: Toggle = mock { on { it.isEnabled() } doReturn false }
    private val fakeAndroidFeaturesHeaderProvider = FakeAndroidFeaturesHeaderProvider("adel_enabled=true")
    private val mockCustomHeaderAllowedChecker: CustomHeaderAllowedChecker = mock()
    private val fakeAppVersionHeaderProvider = object : AppVersionHeaderProvider {
        override fun provide(isStub: Boolean): String = if (isStub) STUB_APP_VERSION else APP_VERSION
    }

    @Before
    fun setup() {
        testee = AndroidFeaturesHeaderPlugin(
            mockDuckDuckGoUrlDetector,
            mockCustomHeaderAllowedChecker,
            mockAndroidBrowserConfigFeature,
            fakeAndroidFeaturesHeaderProvider,
            fakeAppVersionHeaderProvider,
        )

        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(true)
        whenever(mockAndroidBrowserConfigFeature.self()).thenReturn(mockEnabledToggle)
        whenever(mockAndroidBrowserConfigFeature.featuresRequestHeader()).thenReturn(mockEnabledToggle)
        whenever(mockCustomHeaderAllowedChecker.isAllowed()).thenReturn(Allowed(configEnabledForCurrentVersion))
    }

    @Test
    fun whenDuckDuckGoQueryAndVersionAllowedThenSendAutoDeleteStateAndAppVersion() {
        val headers = testee.getHeaders(DDG_QUERY_URL, BrowserMode.REGULAR)

        assertEquals(
            mapOf(
                X_DUCKDUCKGO_ANDROID_HEADER to "adel_enabled=true",
                X_DUCKDUCKGO_ANDROID_APP_VERSION_HEADER to APP_VERSION,
            ),
            headers,
        )
    }

    @Test
    fun whenDuckDuckGoQueryAndVersionNotAllowedThenStillSendAutoDeleteStateWithStubAppVersion() {
        whenever(mockCustomHeaderAllowedChecker.isAllowed()).thenReturn(NotAllowed)

        val headers = testee.getHeaders(DDG_QUERY_URL, BrowserMode.REGULAR)

        assertEquals(
            mapOf(
                X_DUCKDUCKGO_ANDROID_HEADER to "adel_enabled=true",
                X_DUCKDUCKGO_ANDROID_APP_VERSION_HEADER to STUB_APP_VERSION,
            ),
            headers,
        )
    }

    @Test
    fun whenAutoDeleteDisabledThenSendAdelEnabledFalse() {
        fakeAndroidFeaturesHeaderProvider.value = "adel_enabled=false"

        val headers = testee.getHeaders(DDG_QUERY_URL, BrowserMode.REGULAR)

        assertEquals("adel_enabled=false", headers[X_DUCKDUCKGO_ANDROID_HEADER])
    }

    @Test
    fun whenFireModeThenReturnEmptyHeaders() {
        val headers = testee.getHeaders(DDG_QUERY_URL, BrowserMode.FIRE)

        assertTrue(headers.isEmpty())
    }

    @Test
    fun whenFeatureDisabledThenReturnEmptyHeaders() {
        whenever(mockAndroidBrowserConfigFeature.featuresRequestHeader()).thenReturn(mockDisabledToggle)

        val headers = testee.getHeaders(DDG_QUERY_URL, BrowserMode.REGULAR)

        assertTrue(headers.isEmpty())
    }

    @Test
    fun whenParentFeatureDisabledThenReturnEmptyHeaders() {
        whenever(mockAndroidBrowserConfigFeature.self()).thenReturn(mockDisabledToggle)

        val headers = testee.getHeaders(DDG_QUERY_URL, BrowserMode.REGULAR)

        assertTrue(headers.isEmpty())
    }

    @Test
    fun whenNonDuckDuckGoQueryUrlThenReturnEmptyHeaders() {
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(false)

        val headers = testee.getHeaders("https://example.com", BrowserMode.REGULAR)

        assertTrue(headers.isEmpty())
    }

    private class FakeAndroidFeaturesHeaderProvider(var value: String) : AndroidFeaturesHeaderProvider {
        override fun provide(): String = value
    }

    companion object {
        private const val DDG_QUERY_URL = "https://duckduckgo.com/?q=test"
        private const val APP_VERSION = "5.300.0"
        private const val STUB_APP_VERSION = "other_versions"
    }
}
