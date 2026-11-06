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

package com.duckduckgo.app.onboarding

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPrivacyConfigPersistedGateTest {

    private val testee = OnboardingPrivacyConfigPersistedGateImpl()

    @Test
    fun whenConfigAlreadyPersistedThenAwaitReturnsTrueWithoutWaiting() = runTest {
        testee.onPrivacyConfigPersisted()

        assertTrue(testee.awaitPersisted())
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun whenConfigNeverPersistedThenAwaitReturnsFalseAfterTimeout() = runTest {
        assertFalse(testee.awaitPersisted())
        assertEquals(TIMEOUT_MS, testScheduler.currentTime)
    }

    @Test
    fun whenAwaitCalledTwiceAndConfigNeverPersistedThenTimeoutIsOnlyPaidOnce() = runTest {
        assertFalse(testee.awaitPersisted())
        assertFalse(testee.awaitPersisted())

        assertEquals(TIMEOUT_MS, testScheduler.currentTime)
    }

    @Test
    fun whenTwoCallersAwaitConcurrentlyAndConfigNeverPersistedThenTimeoutIsOnlyPaidOnce() = runTest {
        val first = async { testee.awaitPersisted() }
        val second = async { testee.awaitPersisted() }

        assertFalse(first.await())
        assertFalse(second.await())
        assertEquals(TIMEOUT_MS, testScheduler.currentTime)
    }

    @Test
    fun whenConfigPersistedAfterTimeoutThenLaterAwaitReturnsTrue() = runTest {
        assertFalse(testee.awaitPersisted())

        testee.onPrivacyConfigPersisted()

        assertTrue(testee.awaitPersisted())
    }

    private companion object {
        private const val TIMEOUT_MS = 2000L
    }
}
