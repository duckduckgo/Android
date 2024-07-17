package com.duckduckgo.app.pixels.ppropromo.params

import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class AtpAdditionalPixelParamPluginTest {
    @Test
    fun whenUserIsOnboardedToAppTPThenPluginShouldReturnParamTrue() = runTest {
        val appTp: AppTrackingProtection = mock()
        whenever(appTp.isOnboarded()).thenReturn(true)
        val plugin = AtpEnabledAdditionalPixelParamPlugin(appTp)

        assertEquals("atpOnboarded" to "true", plugin.params())
    }

    @Test
    fun whenUserIsNotOnboardedToAppTPThenPluginShouldReturnParamFalse() = runTest {
        val appTp: AppTrackingProtection = mock()
        whenever(appTp.isOnboarded()).thenReturn(false)
        val plugin = AtpEnabledAdditionalPixelParamPlugin(appTp)

        assertEquals("atpOnboarded" to "false", plugin.params())
    }
}
