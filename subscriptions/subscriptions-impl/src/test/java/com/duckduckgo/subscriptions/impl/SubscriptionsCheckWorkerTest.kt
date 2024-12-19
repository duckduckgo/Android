package com.duckduckgo.subscriptions.impl

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import androidx.work.testing.TestWorkerBuilder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SubscriptionsCheckWorkerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val subscriptionsManager: SubscriptionsManager = mock()
    private val workManager: WorkManager = mock()

    private val subscriptionCheckWorker: SubscriptionsCheckWorker = TestWorkerBuilder
        .from(
            ApplicationProvider.getApplicationContext(),
            SubscriptionsCheckWorker::class.java,
        )
        .build()
        .also { worker ->
            worker.workManager = workManager
            worker.subscriptionsManager = subscriptionsManager
        }

    @Test
    fun `when user is signed in using auth v1 and subscription is active then refreshes subscription data`() = runTest {
        whenever(subscriptionsManager.isSignedIn()).thenReturn(true)
        whenever(subscriptionsManager.isSignedInV2()).thenReturn(false)
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)

        subscriptionCheckWorker.doWork()

        verify(subscriptionsManager).fetchAndStoreAllData()
    }

    @Test
    fun `when user is signed in using auth v2 and subscription is active then refreshes subscription data`() = runTest {
        whenever(subscriptionsManager.isSignedIn()).thenReturn(true)
        whenever(subscriptionsManager.isSignedInV2()).thenReturn(true)
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)

        subscriptionCheckWorker.doWork()

        verify(subscriptionsManager).refreshSubscriptionData()
    }

    @Test
    fun `when user is signed in using auth v1 and subscription has WAITING status then refreshes subscription data`() = runTest {
        whenever(subscriptionsManager.isSignedIn()).thenReturn(true)
        whenever(subscriptionsManager.isSignedInV2()).thenReturn(false)
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(SubscriptionStatus.WAITING)

        subscriptionCheckWorker.doWork()

        verify(subscriptionsManager).fetchAndStoreAllData()
    }

    @Test
    fun `when user is signed in using auth v2 and subscription has WAITING status then refreshes subscription data`() = runTest {
        whenever(subscriptionsManager.isSignedIn()).thenReturn(true)
        whenever(subscriptionsManager.isSignedInV2()).thenReturn(true)
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(SubscriptionStatus.WAITING)

        subscriptionCheckWorker.doWork()

        verify(subscriptionsManager).refreshSubscriptionData()
    }

    @Test
    fun `when user is not signed in then cancels work`() = runTest {
        whenever(subscriptionsManager.isSignedIn()).thenReturn(false)
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)

        subscriptionCheckWorker.doWork()

        verify(subscriptionsManager, never()).refreshSubscriptionData()
        verify(subscriptionsManager, never()).fetchAndStoreAllData()
        verify(workManager).cancelUniqueWork(RealSubscriptionsChecker.TAG_WORKER_SUBSCRIPTION_CHECK)
    }

    @Test
    fun `when user is signed using auth v1 and subscription has UNKNOWN status then cancels work`() = runTest {
        whenever(subscriptionsManager.isSignedIn()).thenReturn(true)
        whenever(subscriptionsManager.isSignedInV2()).thenReturn(false)
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)

        subscriptionCheckWorker.doWork()

        verify(subscriptionsManager, never()).refreshSubscriptionData()
        verify(subscriptionsManager, never()).fetchAndStoreAllData()
        verify(workManager).cancelUniqueWork(RealSubscriptionsChecker.TAG_WORKER_SUBSCRIPTION_CHECK)
    }

    @Test
    fun `when user is signed using auth v2 and subscription has UNKNOWN status then cancels work`() = runTest {
        whenever(subscriptionsManager.isSignedIn()).thenReturn(true)
        whenever(subscriptionsManager.isSignedInV2()).thenReturn(true)
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)

        subscriptionCheckWorker.doWork()

        verify(subscriptionsManager, never()).refreshSubscriptionData()
        verify(subscriptionsManager, never()).fetchAndStoreAllData()
        verify(workManager).cancelUniqueWork(RealSubscriptionsChecker.TAG_WORKER_SUBSCRIPTION_CHECK)
    }
}
