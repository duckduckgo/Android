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

package com.duckduckgo.duckchat.impl.rmf

import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IsAIChatEnabledAttributeMatcherPluginTest {
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val mockDuckChatFeatureRepository: DuckChatFeatureRepository = mock()
    private val testee = IsAIChatEnabledAttributeMatcherPlugin(duckChatFeature, mockDuckChatFeatureRepository)

    @Test
    fun `when feature flag enabled and user setting enabled and remote value true, then returns true`() = runTest {
        givenDuckChatState(featureEnabled = true, userEnabled = true)

        val result = testee.evaluate(IsAIChatEnabledMatchingAttribute(remoteValue = true))

        assertEquals(true, result)
    }

    @Test
    fun `when feature flag enabled and user setting disabled and remote value true, then returns false`() = runTest {
        givenDuckChatState(featureEnabled = true, userEnabled = false)

        val result = testee.evaluate(IsAIChatEnabledMatchingAttribute(remoteValue = true))

        assertEquals(false, result)
    }

    @Test
    fun `when feature flag disabled and user setting enabled and remote value true, then returns false`() = runTest {
        givenDuckChatState(featureEnabled = false, userEnabled = true)

        val result = testee.evaluate(IsAIChatEnabledMatchingAttribute(remoteValue = true))

        assertEquals(false, result)
    }

    @Test
    fun `when feature flag disabled and user setting disabled and remote value true, then returns false`() = runTest {
        givenDuckChatState(featureEnabled = false, userEnabled = false)

        val result = testee.evaluate(IsAIChatEnabledMatchingAttribute(remoteValue = true))

        assertEquals(false, result)
    }

    @Test
    fun `when feature flag enabled and user setting enabled and remote value false, then returns false`() = runTest {
        givenDuckChatState(featureEnabled = true, userEnabled = true)

        val result = testee.evaluate(IsAIChatEnabledMatchingAttribute(remoteValue = false))

        assertEquals(false, result)
    }

    @Test
    fun `when feature flag enabled and user setting disabled and remote value false, then returns true`() = runTest {
        givenDuckChatState(featureEnabled = true, userEnabled = false)

        val result = testee.evaluate(IsAIChatEnabledMatchingAttribute(remoteValue = false))

        assertEquals(true, result)
    }

    @Test
    fun `when feature flag disabled and user setting enabled and remote value false, then returns true`() = runTest {
        givenDuckChatState(featureEnabled = false, userEnabled = true)

        val result = testee.evaluate(IsAIChatEnabledMatchingAttribute(remoteValue = false))

        assertEquals(true, result)
    }

    @Test
    fun `when feature flag disabled and user setting disabled and remote value false, then returns true`() = runTest {
        givenDuckChatState(featureEnabled = false, userEnabled = false)

        val result = testee.evaluate(IsAIChatEnabledMatchingAttribute(remoteValue = false))

        assertEquals(true, result)
    }

    @Test
    fun `when user setting changes between evaluations, then result reflects current state`() = runTest {
        givenDuckChatState(featureEnabled = true, userEnabled = true)
        val attribute = IsAIChatEnabledMatchingAttribute(remoteValue = true)

        assertEquals(true, testee.evaluate(attribute))

        whenever(mockDuckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(false)

        assertEquals(false, testee.evaluate(attribute))
    }

    @Test
    fun `when feature flag changes between evaluations, then result reflects current state`() = runTest {
        givenDuckChatState(featureEnabled = true, userEnabled = true)
        val attribute = IsAIChatEnabledMatchingAttribute(remoteValue = true)

        assertEquals(true, testee.evaluate(attribute))

        duckChatFeature.self().setRawStoredState(Toggle.State(enable = false))

        assertEquals(false, testee.evaluate(attribute))
    }

    @Test
    fun `when attribute is different, then returns null without querying user setting`() = runTest {
        val result = testee.evaluate(DaysSinceDuckAiUsedMatchingAttribute(value = 1))

        assertNull(result)
        verify(mockDuckChatFeatureRepository, never()).isDuckChatUserEnabled()
    }

    private suspend fun givenDuckChatState(featureEnabled: Boolean, userEnabled: Boolean) {
        duckChatFeature.self().setRawStoredState(Toggle.State(enable = featureEnabled))
        whenever(mockDuckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(userEnabled)
    }
}
