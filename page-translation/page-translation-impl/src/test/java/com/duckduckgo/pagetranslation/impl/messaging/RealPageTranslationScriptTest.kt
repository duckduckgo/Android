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

package com.duckduckgo.pagetranslation.impl.messaging

import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RealPageTranslationScriptTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val script = RealPageTranslationScript(coroutineRule.testDispatcherProvider)

    @Test
    fun configureTheBundleToTalkOverItsOwnInterface() = runTest {
        val source = script.get(TIME_ORIGIN, SECRET)

        val preferences = """{"platform":{"name":"android"},"messageSecret":"$SECRET",""" +
            """"messageCallback":"DDGPageTranslationCallback","javascriptInterface":"DDGPageTranslation"}"""
        assertTrue(
            source.contains("""initCode({ config: {"features":{},"unprotectedTemporary":[]}, unprotected: [], preferences: $preferences });"""),
        )
        assertFalse(source.contains("$"))
    }

    @Test
    fun runTheBundleOnlyOnceAndOnlyInTheDocumentItWasCreatedFor() = runTest {
        val source = script.get(TIME_ORIGIN, SECRET)

        assertTrue(source.contains("performance.timeOrigin !== $TIME_ORIGIN"))
        assertTrue(source.contains("Object.prototype.hasOwnProperty.call(window, 'DDGPageTranslationCallback')"))
    }

    @Test
    fun useTheSameNamesForEveryUser() {
        val otherUser = RealPageTranslationScript(coroutineRule.testDispatcherProvider)

        assertEquals("DDGPageTranslation", script.interfaceName)
        assertEquals("DDGPageTranslationCallback", script.callbackName)
        assertEquals(script.interfaceName, otherUser.interfaceName)
        assertEquals(script.callbackName, otherUser.callbackName)
    }

    private companion object {
        const val TIME_ORIGIN = 1732634400123.456
        const val SECRET = "secret"
    }
}
