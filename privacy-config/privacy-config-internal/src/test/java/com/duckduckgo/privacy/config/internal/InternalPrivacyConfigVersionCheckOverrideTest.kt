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

package com.duckduckgo.privacy.config.internal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalPrivacyConfigVersionCheckOverrideTest {
    private val testee = InternalPrivacyConfigVersionCheckOverride()

    @Test
    fun scopedOverrideSurvivesDispatcherChangesAndStructuredChildren() = runTest {
        assertFalse(testee.skipVersionCheck())
        testee.skippingVersionCheck {
            assertTrue(testee.skipVersionCheck())
            withContext(StandardTestDispatcher(testScheduler)) {
                assertTrue(testee.skipVersionCheck())
                assertTrue(async { testee.skipVersionCheck() }.await())
            }
            testee.skippingVersionCheck { assertTrue(testee.skipVersionCheck()) }
            assertTrue(testee.skipVersionCheck())
        }
        assertFalse(testee.skipVersionCheck())
    }

    @Test
    fun independentSiblingDoesNotInheritSuspendedOverride() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val marked = launch {
            testee.skippingVersionCheck {
                entered.complete(Unit)
                release.await()
                assertTrue(testee.skipVersionCheck())
            }
        }
        entered.await()
        assertFalse(async { testee.skipVersionCheck() }.await())
        release.complete(Unit)
        marked.join()
        assertFalse(testee.skipVersionCheck())
    }

    @Test
    fun exceptionRestoresCallerContext() = runTest {
        try {
            testee.skippingVersionCheck { throw IllegalStateException("failure") }
        } catch (_: IllegalStateException) {
            assertFalse(testee.skipVersionCheck())
        }
        assertFalse(testee.skipVersionCheck())
    }

    @Test
    fun cancellationRestoresCallerContext() = runTest {
        val entered = CompletableDeferred<Unit>()
        val marked = launch {
            try {
                testee.skippingVersionCheck {
                    entered.complete(Unit)
                    awaitCancellation()
                }
            } finally {
                assertFalse(testee.skipVersionCheck())
            }
        }
        entered.await()
        marked.cancel()
        marked.join()
        assertFalse(testee.skipVersionCheck())
    }
}
