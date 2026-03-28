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

package com.duckduckgo.cookies.impl

import com.cookies.kmp.core.CookiesHostParityFacade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookiesHostParityFacadeIntegrationTest {

    @Test
    fun configFormattingFixtures_matchExpectedOutputs() {
        CookiesHostParityFacade.configFormattingFixtures().forEach { fixture ->
            assertEquals(fixture.name, fixture.expectedOutput, CookiesHostParityFacade.formatConfig(fixture.input))
        }
    }

    @Test
    fun toggleEvaluationFixtures_matchExpectedOutputs() {
        CookiesHostParityFacade.toggleEvaluationFixtures().forEach { fixture ->
            assertEquals(fixture.name, fixture.expectedOutput, CookiesHostParityFacade.evaluateToggle(fixture.input))
        }
    }

    @Test
    fun cookieMatchFixtures_matchExpectedOutputs() {
        CookiesHostParityFacade.cookieMatchFixtures().forEach { fixture ->
            val result = CookiesHostParityFacade.evaluateCookie(fixture.input)
            assertEquals(fixture.name, fixture.expectedOutput, result)
        }
    }

    @Test
    fun cookieMatchFixtures_coverPositiveAndNegativeParityCases() {
        val fixtures = CookiesHostParityFacade.cookieMatchFixtures()

        assertTrue(fixtures.any { it.expectedOutput.hasExcludedCookieName })
        assertTrue(fixtures.any { it.expectedOutput.matchesAllowedDomain })
        assertTrue(fixtures.any { !it.expectedOutput.hasExcludedCookieName })
        assertTrue(fixtures.any { !it.expectedOutput.matchesAllowedDomain })
        assertFalse(fixtures.isEmpty())
    }
}
