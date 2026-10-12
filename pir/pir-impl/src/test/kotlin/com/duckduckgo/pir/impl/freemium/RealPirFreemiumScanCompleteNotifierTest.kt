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

package com.duckduckgo.pir.impl.freemium

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.pir.impl.checker.PirRunMode.SCAN_AND_OPT_OUT
import com.duckduckgo.pir.impl.checker.PirRunMode.SCAN_ONLY
import com.duckduckgo.pir.impl.notifications.PirNotificationManager
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import com.duckduckgo.pir.impl.store.PirFreemiumFirstScanResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class RealPirFreemiumScanCompleteNotifierTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val fakeDataStore = FakePirFreemiumDataStore()
    private val mockNotificationManager: PirNotificationManager = mock()
    private val mockPixelSender: PirPixelSender = mock()

    private lateinit var testee: RealPirFreemiumScanCompleteNotifier

    @Before
    fun setUp() {
        whenever(mockNotificationManager.showFreemiumScanCompleteNotification()).thenReturn(true)
        testee = notifierWith(fakeDataStore, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenNotPendingThenNothingIsPosted() = runTest {
        testee.onScanCompleted(SCAN_ONLY)

        verify(mockNotificationManager, never()).showFreemiumScanCompleteNotification()
        verifyNoInteractions(mockPixelSender)
    }

    @Test
    fun whenPendingAndScanOnlyThenNotificationIsPostedAndSentPixelFires() = runTest {
        fakeDataStore.markScanCompleteNotificationPending()

        testee.onScanCompleted(SCAN_ONLY)

        verify(mockNotificationManager).showFreemiumScanCompleteNotification()
        verify(mockPixelSender).reportFreemiumScanCompleteNotificationSent()
    }

    @Test
    fun whenPendingAndScanOnlyThenALaterCompletionPostsNothing() = runTest {
        fakeDataStore.markScanCompleteNotificationPending()

        testee.onScanCompleted(SCAN_ONLY)
        testee.onScanCompleted(SCAN_ONLY)

        verify(mockNotificationManager, times(1)).showFreemiumScanCompleteNotification()
    }

    @Test
    fun whenNotificationIsNotPostedThenFlagIsStillTakenAndNoPixelFires() = runTest {
        fakeDataStore.markScanCompleteNotificationPending()
        whenever(mockNotificationManager.showFreemiumScanCompleteNotification()).thenReturn(false)

        testee.onScanCompleted(SCAN_ONLY)

        assertFalse(fakeDataStore.isNotificationPending)
        verifyNoInteractions(mockPixelSender)
    }

    @Test
    fun whenPendingAndScanAndOptOutThenFlagIsClearedWithoutPosting() = runTest {
        fakeDataStore.markScanCompleteNotificationPending()

        testee.onScanCompleted(SCAN_AND_OPT_OUT)

        assertFalse(fakeDataStore.isNotificationPending)
        verify(mockNotificationManager, never()).showFreemiumScanCompleteNotification()
        verifyNoInteractions(mockPixelSender)
    }

    @Test
    fun whenDataStoreFailsThenNothingIsPostedAndNoExceptionEscapes() = runTest {
        fakeDataStore.markScanCompleteNotificationPending()
        fakeDataStore.takeError = IllegalStateException("prefs")

        testee.onScanCompleted(SCAN_ONLY)

        verify(mockNotificationManager, never()).showFreemiumScanCompleteNotification()
    }

    @Test
    fun whenCancelledWhileTakingThenCancellationPropagates() = runTest {
        fakeDataStore.markScanCompleteNotificationPending()
        fakeDataStore.takeError = CancellationException("cancelled")

        try {
            testee.onScanCompleted(SCAN_ONLY)
            fail("Expected CancellationException to propagate")
        } catch (_: CancellationException) {
            // expected
        }
    }

    @Test
    fun whenTwoRunsCompleteConcurrentlyThenNotificationIsPostedOnce() = runBlocking<Unit> {
        // Holding the flag between the read and the clear makes both runs see it as pending unless the notifier serialises them.
        val racyDataStore = FakePirFreemiumDataStore(beforeClear = { Thread.sleep(100) })
        racyDataStore.markScanCompleteNotificationPending()
        val notifier = notifierWith(racyDataStore, DefaultDispatcherProvider())

        listOf(
            async { notifier.onScanCompleted(SCAN_ONLY) },
            async { notifier.onScanCompleted(SCAN_ONLY) },
        ).awaitAll()

        verify(mockNotificationManager, times(1)).showFreemiumScanCompleteNotification()
    }

    private fun notifierWith(
        dataStore: PirFreemiumDataStore,
        dispatcherProvider: DispatcherProvider,
    ) = RealPirFreemiumScanCompleteNotifier(
        pirFreemiumDataStore = dataStore,
        pirNotificationManager = mockNotificationManager,
        pirPixelSender = mockPixelSender,
        dispatcherProvider = dispatcherProvider,
    )

    private class FakePirFreemiumDataStore(
        private val beforeClear: () -> Unit = {},
    ) : PirFreemiumDataStore {
        @Volatile
        var isNotificationPending = false
        var takeError: Exception? = null

        override val didActivate: Boolean = false
        override val firstProfileSavedTimestamp: Long = 0L
        override val firstScanResult: PirFreemiumFirstScanResult? = null
        override fun activate(timestampMillis: Long) = Unit
        override fun recordFirstScanResult(result: PirFreemiumFirstScanResult) = Unit
        override fun clearFirstScanResult() = Unit

        override fun reset() {
            isNotificationPending = false
        }

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
