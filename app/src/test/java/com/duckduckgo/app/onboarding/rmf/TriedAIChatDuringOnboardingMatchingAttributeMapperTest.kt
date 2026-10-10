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

import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TriedAIChatDuringOnboardingMatchingAttributeMapperTest {
    private val testee = TriedAIChatDuringOnboardingMatchingAttributeMapper()

    @Test
    fun `when key matches and value is true, then returns attribute with remote value true`() {
        val result = testee.map(TriedAIChatDuringOnboardingMatchingAttribute.KEY, JsonMatchingAttribute(value = true))

        assertEquals(TriedAIChatDuringOnboardingMatchingAttribute(remoteValue = true), result)
    }

    @Test
    fun `when key matches and value is false, then returns attribute with remote value false`() {
        val result = testee.map(TriedAIChatDuringOnboardingMatchingAttribute.KEY, JsonMatchingAttribute(value = false))

        assertEquals(TriedAIChatDuringOnboardingMatchingAttribute(remoteValue = false), result)
    }

    @Test
    fun `when key matches and value is missing, then returns null`() {
        val result = testee.map(TriedAIChatDuringOnboardingMatchingAttribute.KEY, JsonMatchingAttribute(value = null, fallback = true))

        assertNull(result)
    }

    @Test
    fun `when key does not match, then returns null`() {
        val result = testee.map("someOtherKey", JsonMatchingAttribute(value = true))

        assertNull(result)
    }

    @Test
    fun `when key differs only by case, then returns null`() {
        val result = testee.map(TriedAIChatDuringOnboardingMatchingAttribute.KEY.uppercase(), JsonMatchingAttribute(value = true))

        assertNull(result)
    }

    @Test
    fun `when key matches and value is not a boolean, then throws ClassCastException`() {
        assertThrows(ClassCastException::class.java) {
            testee.map(TriedAIChatDuringOnboardingMatchingAttribute.KEY, JsonMatchingAttribute(value = "true"))
        }
        assertThrows(ClassCastException::class.java) {
            testee.map(TriedAIChatDuringOnboardingMatchingAttribute.KEY, JsonMatchingAttribute(value = 1.0))
        }
    }
}
