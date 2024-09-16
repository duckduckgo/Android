package com.duckduckgo.networkprotection.impl.pixels

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class NetworkProtectionRetentionPixelSenderTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()
    private val networkProtectionState = mock<NetworkProtectionState>()
    private val networkProtectionPixels = mock<NetworkProtectionPixels>()

    private lateinit var pixelSender: NetworkProtectionRetentionPixelSender

    @Before
    fun setup() {
        runBlocking { whenever(networkProtectionState.isEnabled()).thenReturn(true) }

        pixelSender = NetworkProtectionRetentionPixelSender(
            networkProtectionState,
            networkProtectionPixels,
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun reportSearchRetentionWhenEnabled() = runTest {
        pixelSender.onSearchRetentionAtbRefreshed("v123-1", "v123-2")
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        pixelSender.onSearchRetentionAtbRefreshed("v123-1", "v123-2")

        verify(networkProtectionPixels).reportEnabledOnSearch()
        verifyNoMoreInteractions(networkProtectionPixels)
    }
}
