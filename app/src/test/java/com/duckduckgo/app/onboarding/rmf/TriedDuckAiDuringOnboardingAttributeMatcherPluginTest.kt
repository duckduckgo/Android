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

package com.duckduckgo.app.onboarding.rmf

import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.remote.messaging.impl.models.DefaultBrowser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TriedDuckAiDuringOnboardingAttributeMatcherPluginTest {
    private val mockOnboardingStore: OnboardingStore = mock()
    private val testee = TriedDuckAiDuringOnboardingAttributeMatcherPlugin(mockOnboardingStore)

    @Test
    fun `when user tried DuckAi during onboarding and remote value true, then returns true`() = runTest {
        whenever(mockOnboardingStore.getTriedDuckAi()).thenReturn(true)

        val result = testee.evaluate(TriedDuckAiDuringOnboardingMatchingAttribute(remoteValue = true))

        assertEquals(true, result)
    }

    @Test
    fun `when user did not try DuckAi during onboarding and remote value true, then returns false`() = runTest {
        whenever(mockOnboardingStore.getTriedDuckAi()).thenReturn(false)

        val result = testee.evaluate(TriedDuckAiDuringOnboardingMatchingAttribute(remoteValue = true))

        assertEquals(false, result)
    }

    @Test
    fun `when user tried DuckAi during onboarding and remote value false, then returns false`() = runTest {
        whenever(mockOnboardingStore.getTriedDuckAi()).thenReturn(true)

        val result = testee.evaluate(TriedDuckAiDuringOnboardingMatchingAttribute(remoteValue = false))

        assertEquals(false, result)
    }

    @Test
    fun `when user did not try DuckAi during onboarding and remote value false, then returns true`() = runTest {
        whenever(mockOnboardingStore.getTriedDuckAi()).thenReturn(false)

        val result = testee.evaluate(TriedDuckAiDuringOnboardingMatchingAttribute(remoteValue = false))

        assertEquals(true, result)
    }

    @Test
    fun `when tried DuckAi flag changes between evaluations, then result reflects current state`() = runTest {
        whenever(mockOnboardingStore.getTriedDuckAi()).thenReturn(false)
        val attribute = TriedDuckAiDuringOnboardingMatchingAttribute(remoteValue = true)

        assertEquals(false, testee.evaluate(attribute))

        whenever(mockOnboardingStore.getTriedDuckAi()).thenReturn(true)

        assertEquals(true, testee.evaluate(attribute))
    }

    @Test
    fun `when attribute is different, then returns null without querying onboarding store`() = runTest {
        val result = testee.evaluate(DefaultBrowser(value = true))

        assertNull(result)
        verify(mockOnboardingStore, never()).getTriedDuckAi()
    }
}
