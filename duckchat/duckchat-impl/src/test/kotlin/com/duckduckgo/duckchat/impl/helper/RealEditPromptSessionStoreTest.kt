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

package com.duckduckgo.duckchat.impl.helper

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.ui.nativeinput.edit.AdoptedImage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealEditPromptSessionStoreTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val testee = RealEditPromptSessionStore()

    @Test
    fun whenSessionOpenedThenPayloadIsReadableById() = runTest {
        val payload = EditPromptPayload(
            prompt = "original",
            images = listOf(AdoptedImage(data = "abc", format = "png")),
            files = emptyList(),
        )

        val sessionId = testee.open(payload)

        assertEquals(payload, testee.payload(sessionId))
    }

    @Test
    fun whenResolvedWithSubmissionThenAwaitReturnsIt() = runTest {
        val sessionId = testee.open(EditPromptPayload("original", emptyList(), emptyList()))
        val awaiting = async(coroutineRule.testDispatcher) { testee.await(sessionId) }

        assertEquals(true, awaiting.isActive)
        testee.resolve(sessionId, EditPromptResult.Submitted("edited", emptyList(), emptyList()))

        assertEquals(EditPromptResult.Submitted("edited", emptyList(), emptyList()), awaiting.await())
    }

    @Test
    fun whenResolvedTwiceThenTheFirstResultWins() = runTest {
        val sessionId = testee.open(EditPromptPayload("original", emptyList(), emptyList()))
        val awaiting = async(coroutineRule.testDispatcher) { testee.await(sessionId) }

        assertEquals(true, awaiting.isActive)
        testee.resolve(sessionId, EditPromptResult.Cancelled)
        testee.resolve(sessionId, EditPromptResult.Submitted("edited", emptyList(), emptyList()))

        assertEquals(EditPromptResult.Cancelled, awaiting.await())
    }

    @Test
    fun whenClearedThenPayloadIsGoneAndAwaitCancels() = runTest {
        val sessionId = testee.open(EditPromptPayload("original", emptyList(), emptyList()))
        val awaiting = async(coroutineRule.testDispatcher) { testee.await(sessionId) }

        assertEquals(true, awaiting.isActive)
        testee.clear(sessionId)

        assertNull(testee.payload(sessionId))
        assertEquals(EditPromptResult.Cancelled, awaiting.await())
    }

    @Test
    fun whenAwaitingAnUnknownSessionThenCancelled() = runTest {
        assertEquals(EditPromptResult.Cancelled, testee.await("no-such-session"))
    }

    @Test
    fun whenOneAwaiterIsCancelledThenTheSessionSurvivesForAnother() = runTest {
        val payload = EditPromptPayload("original", emptyList(), emptyList())
        val sessionId = testee.open(payload)
        val firstAwaiter = async(coroutineRule.testDispatcher) { testee.await(sessionId) }
        assertEquals(true, firstAwaiter.isActive)

        // Simulates EditPromptActivity's own observer coroutine being cancelled by a config change
        // not covered by android:configChanges — the session (and the JS helper's own still-pending
        // await, represented here by a second awaiter) must survive it.
        firstAwaiter.cancel()
        firstAwaiter.join()

        assertEquals(payload, testee.payload(sessionId))
        val secondAwaiter = async(coroutineRule.testDispatcher) { testee.await(sessionId) }
        assertEquals(true, secondAwaiter.isActive)

        testee.resolve(sessionId, EditPromptResult.Submitted("edited", emptyList(), emptyList()))

        assertEquals(EditPromptResult.Submitted("edited", emptyList(), emptyList()), secondAwaiter.await())
    }
}
