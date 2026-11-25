package com.duckduckgo.app.browser

import com.duckduckgo.app.browser.trafficquality.AndroidFeaturesHeaderPlugin
import com.duckduckgo.app.browser.trafficquality.AndroidFeaturesHeaderPlugin.Companion.X_DUCKDUCKGO_ANDROID_HEADER
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

    @Before
    fun setup() {
        testee = AndroidFeaturesHeaderPlugin(
            mockDuckDuckGoUrlDetector,
            mockAndroidBrowserConfigFeature,
            fakeAndroidFeaturesHeaderProvider,
        )

        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(true)
        whenever(mockAndroidBrowserConfigFeature.self()).thenReturn(mockEnabledToggle)
        whenever(mockAndroidBrowserConfigFeature.featuresRequestHeader()).thenReturn(mockEnabledToggle)
    }

    @Test
    fun whenDuckDuckGoQueryThenSendOnlyAutoDeleteState() {
        val headers = testee.getHeaders(DDG_QUERY_URL, BrowserMode.REGULAR)

        assertEquals(mapOf(X_DUCKDUCKGO_ANDROID_HEADER to "adel_enabled=true"), headers)
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
    }
}
