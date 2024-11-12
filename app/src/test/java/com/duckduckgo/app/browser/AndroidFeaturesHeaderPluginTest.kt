package com.duckduckgo.app.browser

import com.duckduckgo.app.browser.AndroidFeaturesHeaderPlugin.Companion.TEST_VALUE
import com.duckduckgo.app.browser.AndroidFeaturesHeaderPlugin.Companion.X_DUCKDUCKGO_ANDROID_HEADER
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
    private val mockAndroidHeaderFeature: AndroidHeaderFeature = mock()
    private val mockEnabledToggle: Toggle = mock { on { it.isEnabled() } doReturn true }
    private val mockDisabledToggle: Toggle = mock { on { it.isEnabled() } doReturn false }

    @Before
    fun setup() {
        testee = AndroidFeaturesHeaderPlugin(mockDuckDuckGoUrlDetector, mockAndroidHeaderFeature)
    }

    @Test
    fun whenGetHeadersCalledWithDuckDuckGoUrlAndFeatureEnabledThenReturnCorrectHeader() {
        val url = "duckduckgo_search_url"
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(true)
        whenever(mockAndroidHeaderFeature.self()).thenReturn(mockEnabledToggle)

        val headers = testee.getHeaders(url)

        assertEquals(TEST_VALUE, headers[X_DUCKDUCKGO_ANDROID_HEADER])
    }

    @Test
    fun whenGetHeadersCalledWithDuckDuckGoUrlAndFeatureDisabledThenReturnEmptyMap() {
        val url = "duckduckgo_search_url"
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(true)
        whenever(mockAndroidHeaderFeature.self()).thenReturn(mockDisabledToggle)

        val headers = testee.getHeaders(url)

        assertTrue(headers.isEmpty())
    }

    @Test
    fun whenGetHeadersCalledWithNonDuckDuckGoUrlAndFeatureEnabledThenReturnEmptyMap() {
        val url = "non_duckduckgo_search_url"
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(false)
        whenever(mockAndroidHeaderFeature.self()).thenReturn(mockEnabledToggle)

        val headers = testee.getHeaders(url)

        assertTrue(headers.isEmpty())
    }

    @Test
    fun whenGetHeadersCalledWithNonDuckDuckGoUrlAndFeatureDisabledThenReturnEmptyMap() {
        val url = "non_duckduckgo_search_url"
        whenever(mockDuckDuckGoUrlDetector.isDuckDuckGoQueryUrl(any())).thenReturn(false)
        whenever(mockAndroidHeaderFeature.self()).thenReturn(mockDisabledToggle)

        val headers = testee.getHeaders(url)

        assertTrue(headers.isEmpty())
    }
}
