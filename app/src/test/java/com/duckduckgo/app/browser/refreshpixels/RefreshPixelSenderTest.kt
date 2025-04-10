package com.duckduckgo.app.browser.refreshpixels

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.customtabs.CustomTabPixelNames
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@RunWith(AndroidJUnit4::class)
@SuppressLint("DenyListedApi")
class RefreshPixelSenderTest {

    private val mockPixel: Pixel = mock()

    private lateinit var testee: DuckDuckGoRefreshPixelSender

    @Before
    fun setUp() {
        testee = DuckDuckGoRefreshPixelSender(
            pixel = mockPixel,
        )
    }

    @Test
    fun whenSendMenuRefreshPixelsThenPixelsFired() {
        testee.sendMenuRefreshPixels()

        verify(mockPixel).fire(
            pixel = AppPixelName.MENU_ACTION_REFRESH_PRESSED,
        )
        verify(mockPixel).fire(
            pixel = AppPixelName.REFRESH_ACTION_DAILY_PIXEL,
            type = Daily(),
        )
    }

    @Test
    fun whenSendPullToRefreshPixelsThenPixelsFired() {
        testee.sendPullToRefreshPixels()

        verify(mockPixel).fire(
            pixel = AppPixelName.BROWSER_PULL_TO_REFRESH,
        )
        verify(mockPixel).fire(
            pixel = AppPixelName.REFRESH_ACTION_DAILY_PIXEL,
            type = Daily(),
        )
    }

    @Test
    fun whenSendCustomTabRefreshPixelThenCorrectPixelFired() {
        testee.sendCustomTabRefreshPixel()

        verify(mockPixel).fire(CustomTabPixelNames.CUSTOM_TABS_MENU_REFRESH)
    }
}
