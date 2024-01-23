package com.duckduckgo.app.browser.pageloadpixel

import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.TestScope
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private const val VALID_URL = "wikipedia.org"
private const val INVALID_URL = "example.com"
class PageLoadedHandlerTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val deviceInfo: DeviceInfo = mock()
    private val webViewVersionProvider: WebViewVersionProvider = mock()
    private val pageLoadedPixelDao: PageLoadedPixelDao = mock()
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature = mock()
    private val toggle: Toggle = mock()

    private val testee = RealPageLoadedHandler(
        deviceInfo,
        webViewVersionProvider,
        pageLoadedPixelDao,
        TestScope(),
        coroutinesTestRule.testDispatcherProvider,
        androidBrowserConfigFeature,
    )

    @Before
    fun before() {
        whenever(webViewVersionProvider.getMajorVersion()).thenReturn("1")
        whenever(deviceInfo.appVersion).thenReturn("1")
        whenever(androidBrowserConfigFeature.optimizeTrackerEvaluation()).thenReturn(toggle)
        whenever(toggle.isEnabled()).thenReturn(true)
    }

    @Test
    fun whenInvokingWithValidUrlThenPixelIsAdded() {
        testee.invoke(VALID_URL, 0L, 10L)
        val argumentCaptor = argumentCaptor<PageLoadedPixelEntity>()
        verify(pageLoadedPixelDao).add(argumentCaptor.capture())
        Assert.assertEquals(10L, argumentCaptor.firstValue.elapsedTime)
        Assert.assertEquals(true, argumentCaptor.firstValue.trackerOptimizationEnabled)
    }

    @Test
    fun whenInvokingWithInvalidUrlThenPixelIsAdded() {
        testee.invoke(INVALID_URL, 0L, 10L)
        verify(pageLoadedPixelDao, never()).add(any())
    }
}
