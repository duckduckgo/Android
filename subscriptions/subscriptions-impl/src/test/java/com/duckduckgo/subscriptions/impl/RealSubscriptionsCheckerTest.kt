package com.duckduckgo.subscriptions.impl

import android.content.Context
import androidx.lifecycle.Lifecycle.State.INITIALIZED
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealSubscriptionsCheckerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val processLifecycleOwner = TestLifecycleOwner(initialState = INITIALIZED)
    private val subscriptionsManager: SubscriptionsManager = mock()
    private lateinit var workManager: WorkManager
    private lateinit var testDriver: TestDriver
    private lateinit var subscriptionsChecker: RealSubscriptionsChecker

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
        testDriver = WorkManagerTestInitHelper.getTestDriver(context)!!

        subscriptionsChecker = RealSubscriptionsChecker(
            workManager = workManager,
            subscriptionsManager = subscriptionsManager,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )

        processLifecycleOwner.lifecycle.addObserver(subscriptionsChecker)
    }

    @Test
    fun `when app started and has active subscription then enqueues checker worker`() = runTest {
        whenever(subscriptionsManager.isSignedIn()).thenReturn(true)
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)

        processLifecycleOwner.currentState = STARTED

        val workInfos = workManager.getWorkInfosForUniqueWork(RealSubscriptionsChecker.TAG_WORKER_SUBSCRIPTION_CHECK).get()
        assertEquals(1, workInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.single().state)
    }

    @Test
    fun `when user is not signed in then does not enqueue checker worker`() = runTest {
        whenever(subscriptionsManager.isSignedIn()).thenReturn(false)
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)

        subscriptionsChecker.runChecker()

        val workInfos = workManager.getWorkInfosForUniqueWork(RealSubscriptionsChecker.TAG_WORKER_SUBSCRIPTION_CHECK).get()
        assertTrue(workInfos.isEmpty())
    }

    @Test
    fun `when user is signed in and subscription has UNKNOWN status then does not enqueue checker worker`() = runTest {
        whenever(subscriptionsManager.isSignedIn()).thenReturn(true)
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)

        subscriptionsChecker.runChecker()

        val workInfos = workManager.getWorkInfosForUniqueWork(RealSubscriptionsChecker.TAG_WORKER_SUBSCRIPTION_CHECK).get()
        assertTrue(workInfos.isEmpty())
    }

    @Test
    fun `when user is signed in and subscription has WAITING status then enqueues checker worker`() = runTest {
        whenever(subscriptionsManager.isSignedIn()).thenReturn(true)
        whenever(subscriptionsManager.subscriptionStatus()).thenReturn(SubscriptionStatus.WAITING)

        subscriptionsChecker.runChecker()

        val workInfos = workManager.getWorkInfosForUniqueWork(RealSubscriptionsChecker.TAG_WORKER_SUBSCRIPTION_CHECK).get()
        assertEquals(1, workInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.single().state)
    }
}
