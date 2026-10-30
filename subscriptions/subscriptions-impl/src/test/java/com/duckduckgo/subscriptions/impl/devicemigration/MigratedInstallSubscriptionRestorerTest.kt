/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.subscriptions.impl.devicemigration

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.subscriptions.impl.RealSubscriptionsManager.Companion.SUBSCRIPTION_NOT_FOUND_ERROR
import com.duckduckgo.subscriptions.impl.RealSubscriptionsManager.RecoverSubscriptionResult
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.billing.PlayBillingManager
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MigratedInstallSubscriptionRestorerTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val deviceMigration: DeviceMigration = mock()
    private val subscriptionsManager: SubscriptionsManager = mock()
    private val playBillingManager: PlayBillingManager = mock { on { isReady } doReturn true }
    private val preferences = InMemorySharedPreferences()
    private val sharedPreferencesProvider: SharedPreferencesProvider = mock {
        on { getSharedPreferences(any(), any(), any()) } doReturn preferences
    }

    private val testee = MigratedInstallSubscriptionRestorer(
        deviceMigration = deviceMigration,
        subscriptionsManager = subscriptionsManager,
        playBillingManager = playBillingManager,
        sharedPreferencesProvider = sharedPreferencesProvider,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        appCoroutineScope = coroutineTestRule.testScope,
    )

    @Test
    fun whenMigratedAndNotSignedInThenRecoverFromStoreCalledOnce() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(subscriptionsManager.isSignedIn()).thenReturn(false)
        whenever(subscriptionsManager.recoverSubscriptionFromStore(null)).thenReturn(RecoverSubscriptionResult.Failure(SUBSCRIPTION_NOT_FOUND_ERROR))

        startApp()
        startApp()

        verify(subscriptionsManager, times(1)).recoverSubscriptionFromStore(null)
    }

    @Test
    fun whenMigratedAndAlreadySignedInThenNoRecover() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(subscriptionsManager.isSignedIn()).thenReturn(true)

        startApp()

        verify(subscriptionsManager, never()).recoverSubscriptionFromStore(null)
    }

    @Test
    fun whenNotMigratedThenNoRecover() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(false)
        whenever(subscriptionsManager.isSignedIn()).thenReturn(false)

        startApp()

        verify(subscriptionsManager, never()).recoverSubscriptionFromStore(null)
    }

    @Test
    fun whenRecoverIsInconclusiveThenRetriedOnNextStart() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(subscriptionsManager.isSignedIn()).thenReturn(false)
        whenever(subscriptionsManager.recoverSubscriptionFromStore(null))
            .thenReturn(RecoverSubscriptionResult.Failure("Store login error: PurchaseInfoNotAvailable: billing_client_not_ready"))

        startApp()
        startApp()

        verify(subscriptionsManager, times(2)).recoverSubscriptionFromStore(null)
    }

    @Test
    fun whenRecoverThrowsThenRetriedOnNextStart() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(subscriptionsManager.isSignedIn()).thenReturn(false)
        whenever(subscriptionsManager.recoverSubscriptionFromStore(null)).thenAnswer { throw RuntimeException("boom") }

        startApp()
        startApp()

        verify(subscriptionsManager, times(2)).recoverSubscriptionFromStore(null)
    }

    @Test
    fun whenRecoverSucceedsThenNotAttemptedAgain() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(subscriptionsManager.isSignedIn()).thenReturn(false)
        whenever(subscriptionsManager.recoverSubscriptionFromStore(null)).thenReturn(RecoverSubscriptionResult.Success(mock()))

        startApp()
        startApp()

        verify(subscriptionsManager, times(1)).recoverSubscriptionFromStore(null)
    }

    @Test
    fun whenInconclusiveRepeatedlyThenAttemptsCappedAtThree() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(subscriptionsManager.isSignedIn()).thenReturn(false)
        whenever(subscriptionsManager.recoverSubscriptionFromStore(null)).thenReturn(RecoverSubscriptionResult.Failure("transient"))

        repeat(5) { startApp() }

        verify(subscriptionsManager, times(3)).recoverSubscriptionFromStore(null)
    }

    @Test
    fun whenBillingBecomesReadyThenRecoverCalled() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(subscriptionsManager.isSignedIn()).thenReturn(false)
        whenever(playBillingManager.isReady).thenReturn(false, false, true)
        whenever(subscriptionsManager.recoverSubscriptionFromStore(null)).thenReturn(RecoverSubscriptionResult.Success(mock()))

        startApp()

        verify(subscriptionsManager, times(1)).recoverSubscriptionFromStore(null)
    }

    @Test
    fun whenBillingNeverReadyThenNoRecoverAndAttemptCounted() = runTest {
        whenever(deviceMigration.isMigratedInstall()).thenReturn(true)
        whenever(subscriptionsManager.isSignedIn()).thenReturn(false)
        whenever(playBillingManager.isReady).thenReturn(false)

        repeat(4) { startApp() }

        verify(subscriptionsManager, never()).recoverSubscriptionFromStore(null)
        verify(playBillingManager, atLeast(3)).isReady
        whenever(playBillingManager.isReady).thenReturn(true)
        startApp()
        verify(subscriptionsManager, never()).recoverSubscriptionFromStore(null)
    }

    private fun startApp() {
        testee.onCreate(mock())
        coroutineTestRule.testScope.advanceUntilIdle()
    }
}
