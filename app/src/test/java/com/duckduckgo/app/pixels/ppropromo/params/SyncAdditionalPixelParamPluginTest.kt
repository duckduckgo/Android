package com.duckduckgo.app.pixels.ppropromo.params

import com.duckduckgo.sync.api.DeviceSyncState
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncAdditionalPixelParamPluginTest {
    @Test
    fun whenUserNotSignedInForSyncThenPluginShouldReturnParamFalse() = runTest {
        val deviceSyncState: DeviceSyncState = mock()
        whenever(deviceSyncState.isUserSignedInOnDevice()).thenReturn(false)
        val plugin = SyncedUsedAdditionalPixelParamPlugin(deviceSyncState)

        Assert.assertEquals("syncUsed" to "false", plugin.params())
    }

    @Test
    fun whenUserIsSignedInForSyncThenPluginShouldReturnParamFalse() = runTest {
        val deviceSyncState: DeviceSyncState = mock()
        whenever(deviceSyncState.isUserSignedInOnDevice()).thenReturn(true)
        val plugin = SyncedUsedAdditionalPixelParamPlugin(deviceSyncState)

        Assert.assertEquals("syncUsed" to "true", plugin.params())
    }
}
