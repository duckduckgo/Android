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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

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
    fun whenSyncSucceedsWhileACheckIsRunningThenDoesNotStartAnotherCheck() = runTest {
        val running = checker.suspendNextCheck()

        hook.onSyncSucceeded()
        hook.onSyncSucceeded()
        running.complete(AuditLogCheckResult.Initialised)

        assertEquals(1, checker.checkCount)
    }

    @Test
    fun whenPreviousCheckFinishedThenNextSyncChecksAgain() = runTest {
        val running = checker.suspendNextCheck()
        hook.onSyncSucceeded()
        running.complete(AuditLogCheckResult.Initialised)

        hook.onSyncSucceeded()

        assertEquals(2, checker.checkCount)
    }

    @Test
    fun whenPreviousCheckWasCancelledThenNextSyncChecksAgain() = runTest {
        val running = checker.suspendNextCheck()
        hook.onSyncSucceeded()
        running.completeExceptionally(CancellationException())

        hook.onSyncSucceeded()

        assertEquals(2, checker.checkCount)
    }

    @Test
    fun whenCheckThrowsThenHookDoesNotThrowAndNextSyncChecksAgain() = runTest {
        checker.throwOnNextCheck = IllegalStateException()
        hook.onSyncSucceeded()

        hook.onSyncSucceeded()

        assertEquals(2, checker.checkCount)
    }

    private class FakeAuditLogChecker : AuditLogChecker {
        var checkCount = 0
            private set
        private var nextCheck: CompletableDeferred<AuditLogCheckResult>? = null
        var throwOnNextCheck: Exception? = null

        fun suspendNextCheck(): CompletableDeferred<AuditLogCheckResult> = CompletableDeferred<AuditLogCheckResult>().also { nextCheck = it }

        override suspend fun check(): AuditLogCheckResult {
            checkCount++
            throwOnNextCheck?.let {
                throwOnNextCheck = null
                throw it
            }
            val pending = nextCheck ?: return AuditLogCheckResult.Checked(newLogins = 0, renamedLogins = 0)
            nextCheck = null
            return pending.await()
        }
    }
}
