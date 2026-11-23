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

package com.duckduckgo.sync.impl.auditlog

import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RealAuditLogSyncHookTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val checker = FakeAuditLogChecker()

    private val hook = RealAuditLogSyncHook(
        checker = checker,
        appCoroutineScope = coroutineTestRule.testScope,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenSyncSucceedsThenChecks() = runTest {
        hook.onSyncSucceeded()

        assertEquals(1, checker.checkCount)
    }

    @Test
    fun whenCheckThrowsThenHookDoesNotThrowAndNextSyncChecksAgain() = runTest {
        checker.throwOnNextCheck = IllegalStateException()
        hook.onSyncSucceeded()

        hook.onSyncSucceeded()

        assertEquals(2, checker.checkCount)
    }

    @Test
    fun whenNoCheckHasStartedThenAwaitReturnsAtOnce() = runTest {
        hook.awaitRunningCheck()

        assertEquals(0, checker.checkCount)
    }

    @Test
    fun whenCheckIsRunningThenAwaitReturnsOnlyAfterItEnds() = runTest {
        val release = CompletableDeferred<Unit>()
        checker.release = release
        hook.onSyncSucceeded()

        val waiting = async { hook.awaitRunningCheck() }
        runCurrent()
        assertFalse(waiting.isCompleted)

        release.complete(Unit)
        runCurrent()
        assertTrue(waiting.isCompleted)
    }

    @Test
    fun whenSyncSucceedsWhileCheckIsRunningThenAwaitStillWaitsForThatCheck() = runTest {
        val release = CompletableDeferred<Unit>()
        checker.release = release
        hook.onSyncSucceeded()
        hook.onSyncSucceeded()

        val waiting = async { hook.awaitRunningCheck() }
        runCurrent()
        assertFalse(waiting.isCompleted)
        assertEquals(1, checker.checkCount)

        release.complete(Unit)
        runCurrent()
        assertTrue(waiting.isCompleted)
    }

    @Test
    fun whenCheckThrowsThenAwaitReturns() = runTest {
        checker.throwOnNextCheck = IllegalStateException()
        hook.onSyncSucceeded()

        hook.awaitRunningCheck()

        assertEquals(1, checker.checkCount)
    }

    private class FakeAuditLogChecker : AuditLogChecker {
        var checkCount = 0
            private set
        var throwOnNextCheck: Exception? = null
        var release: CompletableDeferred<Unit>? = null

        override suspend fun check(): AuditLogCheckResult {
            checkCount++
            release?.await()
            throwOnNextCheck?.let {
                throwOnNextCheck = null
                throw it
            }
            return AuditLogCheckResult.Checked(newLogins = 0, renamedLogins = 0)
        }
    }
}
