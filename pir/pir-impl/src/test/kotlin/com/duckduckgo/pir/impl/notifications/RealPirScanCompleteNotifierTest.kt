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

package com.duckduckgo.pir.impl.notifications

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.pir.impl.checker.PirRunMode.SCAN_AND_OPT_OUT
import com.duckduckgo.pir.impl.checker.PirRunMode.SCAN_ONLY
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.store.PirDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class RealPirScanCompleteNotifierTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val fakeDataStore = FakeScanCompleteFlagDataStore()
    private val mockNotificationManager: PirNotificationManager = mock()
    private val mockPixelSender: PirPixelSender = mock()

    private lateinit var testee: RealPirScanCompleteNotifier

    @Before
    fun setUp() {
        whenever(mockNotificationManager.showScanCompleteNotification(any())).thenReturn(true)
        testee = notifierWith(fakeDataStore, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenInitialScanStartsThenScanCompleteNotificationIsMarkedPending() {
        testee.onInitialScanStarted()

        assertTrue(fakeDataStore.isNotificationPending)
    }

    @Test
    fun whenNotPendingThenNothingIsPosted() = runTest {
        testee.onRunCompleted(SCAN_ONLY)

        verify(mockNotificationManager, never()).showScanCompleteNotification(any())
        verifyNoInteractions(mockPixelSender)
    }

    @Test
    fun whenPendingAndScanOnlyThenFreemiumNotificationIsPostedAndOnlyFreemiumSentPixelFires() = runTest {
        testee.onInitialScanStarted()

        testee.onRunCompleted(SCAN_ONLY)

        verify(mockNotificationManager).showScanCompleteNotification(SCAN_ONLY)
        verify(mockPixelSender).reportFreemiumScanCompleteNotificationSent()
        verify(mockPixelSender, never()).reportScanCompleteNotificationSent()
    }

    @Test
    fun whenPendingAndScanAndOptOutThenPaidNotificationIsPostedAndOnlyPaidSentPixelFires() = runTest {
        testee.onInitialScanStarted()

        testee.onRunCompleted(SCAN_AND_OPT_OUT)

        verify(mockNotificationManager).showScanCompleteNotification(SCAN_AND_OPT_OUT)
        verify(mockPixelSender).reportScanCompleteNotificationSent()
        verify(mockPixelSender, never()).reportFreemiumScanCompleteNotificationSent()
    }

    @Test
    fun whenPendingThenALaterCompletionPostsNothing() = runTest {
        testee.onInitialScanStarted()

        testee.onRunCompleted(SCAN_AND_OPT_OUT)
        testee.onRunCompleted(SCAN_AND_OPT_OUT)

        verify(mockNotificationManager, times(1)).showScanCompleteNotification(any())
    }

    @Test
    fun whenScanOnlyNotificationIsNotPostedThenFlagIsStillTakenAndNoPixelFires() = runTest {
        testee.onInitialScanStarted()
        whenever(mockNotificationManager.showScanCompleteNotification(any())).thenReturn(false)

        testee.onRunCompleted(SCAN_ONLY)

        assertFalse(fakeDataStore.isNotificationPending)
        verifyNoInteractions(mockPixelSender)
    }

    @Test
    fun whenScanAndOptOutNotificationIsNotPostedThenFlagIsStillTakenAndNoPixelFires() = runTest {
        testee.onInitialScanStarted()
        whenever(mockNotificationManager.showScanCompleteNotification(any())).thenReturn(false)

        testee.onRunCompleted(SCAN_AND_OPT_OUT)

        assertFalse(fakeDataStore.isNotificationPending)
        verifyNoInteractions(mockPixelSender)
    }

    @Test
    fun whenDataStoreFailsThenNothingIsPostedAndNoExceptionEscapes() = runTest {
        testee.onInitialScanStarted()
        fakeDataStore.takeError = IllegalStateException("prefs")

        testee.onRunCompleted(SCAN_ONLY)

        verify(mockNotificationManager, never()).showScanCompleteNotification(any())
    }

    @Test
    fun whenPostingFailsThenNoPixelFiresAndNoExceptionEscapes() = runTest {
        testee.onInitialScanStarted()
        whenever(mockNotificationManager.showScanCompleteNotification(any())).thenAnswer { throw IllegalStateException("post") }

        testee.onRunCompleted(SCAN_AND_OPT_OUT)

        verifyNoInteractions(mockPixelSender)
    }

    @Test
    fun whenCancelledWhileTakingThenCancellationPropagates() = runTest {
        testee.onInitialScanStarted()
        fakeDataStore.takeError = CancellationException("cancelled")

        try {
            testee.onRunCompleted(SCAN_ONLY)
            fail("Expected CancellationException to propagate")
        } catch (_: CancellationException) {
            // expected
        }
    }

    @Test
    fun whenTwoRunsCompleteConcurrentlyThenNotificationIsPostedOnce() = runBlocking<Unit> {
        // Holding the flag between the read and the clear makes both runs see it as pending unless the notifier serialises them.
        val racyDataStore = FakeScanCompleteFlagDataStore(beforeClear = { Thread.sleep(100) })
        racyDataStore.markScanCompleteNotificationPending()
        val notifier = notifierWith(racyDataStore, DefaultDispatcherProvider())

        listOf(
            async { notifier.onRunCompleted(SCAN_AND_OPT_OUT) },
            async { notifier.onRunCompleted(SCAN_AND_OPT_OUT) },
        ).awaitAll()

        verify(mockNotificationManager, times(1)).showScanCompleteNotification(any())
    }

    private fun notifierWith(
        dataStore: PirDataStore,
        dispatcherProvider: DispatcherProvider,
    ) = RealPirScanCompleteNotifier(
        pirDataStore = dataStore,
        pirNotificationManager = mockNotificationManager,
        pirPixelSender = mockPixelSender,
        dispatcherProvider = dispatcherProvider,
    )

    // Members unrelated to the flag delegate to a mock.
    private class FakeScanCompleteFlagDataStore(
        private val beforeClear: () -> Unit = {},
    ) : PirDataStore by mock<PirDataStore>() {
        @Volatile
        var isNotificationPending = false
        var takeError: Exception? = null

        override fun markScanCompleteNotificationPending() {
            isNotificationPending = true
        }

        override fun takePendingScanCompleteNotification(): Boolean {
            takeError?.let { throw it }
            if (!isNotificationPending) return false
            beforeClear()
            isNotificationPending = false
            return true
        }
    }
}
