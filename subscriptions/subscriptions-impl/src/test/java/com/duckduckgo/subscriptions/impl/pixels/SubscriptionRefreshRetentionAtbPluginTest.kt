package com.duckduckgo.subscriptions.impl.pixels

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SubscriptionRefreshRetentionAtbPluginTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val subscriptionsManager: SubscriptionsManager = mock()
    private val pixelSender: SubscriptionPixelSender = mock()

    private val subject = SubscriptionRefreshRetentionAtbPlugin(
        coroutineScope = coroutineRule.testScope,
        subscriptionsManager = subscriptionsManager,
        pixelSender = pixelSender,
    )

    @Test
    fun `when subscription is active then pixel is sent`() = runTest {
        whenever(subscriptionsManager.hasSubscription()).thenReturn(true)

        subject.onAppRetentionAtbRefreshed()

        verify(pixelSender).reportSubscriptionActive()
    }

    @Test
    fun `when subscription is not active then pixel is not sent`() = runTest {
        whenever(subscriptionsManager.hasSubscription()).thenReturn(false)

        subject.onAppRetentionAtbRefreshed()

        verify(pixelSender, never()).reportSubscriptionActive()
    }
}
