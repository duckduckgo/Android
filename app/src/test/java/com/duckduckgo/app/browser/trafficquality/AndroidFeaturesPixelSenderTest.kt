package com.duckduckgo.app.browser.trafficquality

import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.privacy.config.api.Gpc
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class AndroidFeaturesPixelSenderTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockAutoconsent = mock<Autoconsent>()
    private val mockGpc = mock<Gpc>()
    private val mockAppTrackingProtection = mock<AppTrackingProtection>()
    private val mockNetworkProtectionState = mock<NetworkProtectionState>()
    private val mockPixel = mock<Pixel>()

    private lateinit var pixelSender: AndroidFeaturesPixelSender

    @Before
    fun setup() {
        pixelSender = AndroidFeaturesPixelSender(
            mockAutoconsent,
            mockGpc,
            mockAppTrackingProtection,
            mockNetworkProtectionState,
            mockPixel,
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun reportFeaturesEnabledOrDisabledWhenEnabledOrDisabled() = runTest {
        whenever(mockAutoconsent.isAutoconsentEnabled()).thenReturn(false)
        whenever(mockGpc.isEnabled()).thenReturn(true)
        whenever(mockAppTrackingProtection.isEnabled()).thenReturn(false)
        whenever(mockNetworkProtectionState.isEnabled()).thenReturn(true)

        pixelSender.onSearchRetentionAtbRefreshed("v123-1", "v123-2")

        verify(mockPixel).fire(
            AppPixelName.FEATURES_ENABLED_AT_SEARCH_TIME,
            mapOf(
                AndroidFeaturesPixelSender.PARAM_COOKIE_POP_UP_MANAGEMENT_ENABLED to "false",
                AndroidFeaturesPixelSender.PARAM_GLOBAL_PRIVACY_CONTROL_ENABLED to "true",
                AndroidFeaturesPixelSender.PARAM_APP_TRACKING_PROTECTION_ENABLED to "false",
                AndroidFeaturesPixelSender.PARAM_PRIVACY_PRO_VPN_ENABLED to "true",
            ),
        )
        verifyNoMoreInteractions(mockPixel)
    }
}
