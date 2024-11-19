package com.duckduckgo.app.browser.trafficquality

import com.duckduckgo.app.browser.trafficquality.RealQualityAppVersionProvider.Companion.APP_VERSION_QUALITY_DEFAULT_VALUE
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class AndroidAppVersionPixelSenderTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockAppVersionProvider = mock<QualityAppVersionProvider>()
    private val mockPixel = mock<Pixel>()

    private lateinit var pixelSender: AndroidAppVersionPixelSender

    @Before
    fun setup() {
        pixelSender = AndroidAppVersionPixelSender(
            mockAppVersionProvider,
            mockPixel,
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun reportFeaturesEnabledOrDisabledWhenEnabledOrDisabled() = runTest {
        whenever(mockAppVersionProvider.provide()).thenReturn(APP_VERSION_QUALITY_DEFAULT_VALUE)

        pixelSender.onSearchRetentionAtbRefreshed("v123-1", "v123-2")

        verify(mockPixel).fire(
            AppPixelName.APP_VERSION_AT_SEARCH_TIME,
            mapOf(
                AndroidAppVersionPixelSender.PARAM_APP_VERSION to APP_VERSION_QUALITY_DEFAULT_VALUE,
            ),
        )
        verifyNoMoreInteractions(mockPixel)
    }
}
