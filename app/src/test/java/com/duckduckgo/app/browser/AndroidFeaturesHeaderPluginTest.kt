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
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
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
    private val mockAndroidFeaturesHeaderProvider: AndroidFeaturesHeaderProvider = mock()
    private val mockCustomHeaderGracePeriodChecker: CustomHeaderAllowedChecker = mock()
    private val mockAppVersionHeaderProvider: AppVersionHeaderProvider = mock()

    private val SAMPLE_FEATURE_HEADER = "feature_header"
    private val SAMPLE_APP_VERSION_HEADER = "app_version_header"

    @Before
    fun setup() {
        testee = AndroidFeaturesHeaderPlugin(
            mockDuckDuckGoUrlDetector,
            mockCustomHeaderGracePeriodChecker,
            mockAndroidBrowserConfigFeature,
            mockAndroidFeaturesHeaderProvider,
            mockAppVersionHeaderProvider,
        )

        whenever(mockCustomHeaderGracePeriodChecker.isAllowed()).thenReturn(Allowed(configEnabledForCurrentVersion))
        whenever(mockAppVersionHeaderProvider.provide(any())).thenReturn(SAMPLE_APP_VERSION_HEADER)
    }

    @Test
    fun whenGetHeadersCalledWithDuckDuckGoUrlAndHeaderAllowedWithBothHeadersProvidedThenReturnCorrectHeader() = runTest {
        val url = "duckduckgo_search_url"
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(true)
        whenever(mockAndroidBrowserConfigFeature.self()).thenReturn(mockEnabledToggle)
        whenever(mockAndroidBrowserConfigFeature.featuresRequestHeader()).thenReturn(mockEnabledToggle)
        whenever(mockAndroidFeaturesHeaderProvider.provide(any())).thenReturn(SAMPLE_FEATURE_HEADER)

        val headers = testee.getHeaders(url)

        assertEquals(SAMPLE_FEATURE_HEADER, headers[X_DUCKDUCKGO_ANDROID_HEADER])
        assertEquals(SAMPLE_APP_VERSION_HEADER, headers[X_DUCKDUCKGO_ANDROID_APP_VERSION_HEADER])
    }

    @Test
    fun whenGetHeadersCalledWithDuckDuckGoUrlAndHeaderAllowedWithOnlyAppVersionProvidedThenReturnCorrectHeader() = runTest {
        val url = "duckduckgo_search_url"
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(true)
        whenever(mockAndroidBrowserConfigFeature.self()).thenReturn(mockEnabledToggle)
        whenever(mockAndroidBrowserConfigFeature.featuresRequestHeader()).thenReturn(mockEnabledToggle)
        whenever(mockAndroidFeaturesHeaderProvider.provide(any())).thenReturn(null)

        val headers = testee.getHeaders(url)

        assertEquals(null, headers[X_DUCKDUCKGO_ANDROID_HEADER])
        assertEquals(SAMPLE_APP_VERSION_HEADER, headers[X_DUCKDUCKGO_ANDROID_APP_VERSION_HEADER])
    }

    @Test
    fun whenGetHeadersCalledWithDuckDuckGoUrlAndFeatureDisabledThenReturnEmptyHeaders() {
        val url = "duckduckgo_search_url"
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(true)
        whenever(mockAndroidBrowserConfigFeature.self()).thenReturn(mockEnabledToggle)
        whenever(mockAndroidBrowserConfigFeature.featuresRequestHeader()).thenReturn(mockDisabledToggle)

        val headers = testee.getHeaders(url)

        assertTrue(headers.isEmpty())
    }

    @Test
    fun whenGetHeadersCalledWithDuckDuckGoUrlAndHeaderNotAllowedThenReturnCorrectHeader() {
        val url = "duckduckgo_search_url"
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(true)
        whenever(mockAndroidBrowserConfigFeature.self()).thenReturn(mockEnabledToggle)
        whenever(mockAndroidBrowserConfigFeature.featuresRequestHeader()).thenReturn(mockEnabledToggle)
        whenever(mockCustomHeaderGracePeriodChecker.isAllowed()).thenReturn(NotAllowed)

        val headers = testee.getHeaders(url)

        assertEquals(null, headers[X_DUCKDUCKGO_ANDROID_HEADER])
        assertEquals(SAMPLE_APP_VERSION_HEADER, headers[X_DUCKDUCKGO_ANDROID_APP_VERSION_HEADER])
    }

    @Test
    fun whenGetHeadersCalledWithNonDuckDuckGoUrlAndFeatureEnabledThenReturnEmptyMap() {
        val url = "non_duckduckgo_search_url"
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(false)
        whenever(mockAndroidBrowserConfigFeature.self()).thenReturn(mockEnabledToggle)
        whenever(mockAndroidBrowserConfigFeature.featuresRequestHeader()).thenReturn(mockEnabledToggle)

        val headers = testee.getHeaders(url)

        assertTrue(headers.isEmpty())
    }

    @Test
    fun whenGetHeadersCalledWithNonDuckDuckGoUrlAndFeatureDisabledThenReturnEmptyMap() {
        val url = "non_duckduckgo_search_url"
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(false)
        whenever(mockAndroidBrowserConfigFeature.self()).thenReturn(mockEnabledToggle)
        whenever(mockAndroidBrowserConfigFeature.featuresRequestHeader()).thenReturn(mockDisabledToggle)

        val headers = testee.getHeaders(url)

        assertTrue(headers.isEmpty())
    }
}
