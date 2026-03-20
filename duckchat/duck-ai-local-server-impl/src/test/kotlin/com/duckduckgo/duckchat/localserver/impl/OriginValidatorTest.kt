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

package com.duckduckgo.duckchat.localserver.impl

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OriginValidatorTest {

    private val appBuildConfig: AppBuildConfig = mock()
    private val validator = OriginValidator(appBuildConfig)

    // All tests below run as a Play build (non-internal) unless stated otherwise

    @Test
    fun `allows duckduckgo com origin`() {
        assertTrue(validator.isAllowed(mapOf("origin" to "https://duckduckgo.com")))
    }

    @Test
    fun `allows duck ai origin`() {
        assertTrue(validator.isAllowed(mapOf("origin" to "https://duck.ai")))
    }

    @Test
    fun `rejects missing origin header`() {
        assertFalse(validator.isAllowed(emptyMap<String, String>()))
    }

    @Test
    fun `rejects empty origin value`() {
        assertFalse(validator.isAllowed(mapOf("origin" to "")))
    }

    @Test
    fun `rejects other origins`() {
        assertFalse(validator.isAllowed(mapOf("origin" to "https://evil.com")))
    }

    @Test
    fun `rejects partial match attempts`() {
        assertFalse(validator.isAllowed(mapOf("origin" to "https://evil.duckduckgo.com.evil.com")))
    }

    @Test
    fun `rejects whitespace-only origin value`() {
        assertFalse(validator.isAllowed(mapOf("origin" to "   ")))
    }

    @Test
    fun `rejects uppercase Origin header key`() {
        assertFalse(validator.isAllowed(mapOf("Origin" to "https://duckduckgo.com")))
    }

    @Test
    fun `allows duck ai subdomains in internal builds`() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        assertTrue(validator.isAllowed(mapOf("origin" to "https://mkyong.duck.ai")))
        assertTrue(validator.isAllowed(mapOf("origin" to "https://dev.duck.ai")))
    }

    @Test
    fun `rejects duck ai subdomains in non-internal builds`() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        assertFalse(validator.isAllowed(mapOf("origin" to "https://mkyong.duck.ai")))
    }

    @Test
    fun `rejects arbitrary origins in internal builds`() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        assertFalse(validator.isAllowed(mapOf("origin" to "https://evil.com")))
        assertFalse(validator.isAllowed(emptyMap()))
    }

    @Test
    fun `rejects spoofed duck ai suffix in internal builds`() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        assertFalse(validator.isAllowed(mapOf("origin" to "https://evil.duck.ai.attacker.com")))
        assertFalse(validator.isAllowed(mapOf("origin" to "https://notduck.ai")))
    }
}
