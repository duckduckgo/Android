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

package com.duckduckgo.networkprotection.impl.portprobing

import com.wireguard.config.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class ConfigExtensionsTest {

    @Test
    fun `replacingEndpointPort updates port correctly`() {
        val originalConfig = createTestConfig(port = 443)

        val updatedConfig = originalConfig.replacingEndpointPort(51820)

        assertEquals(51820L, updatedConfig.currentEndpointPort())
        assertNotNull(updatedConfig.serverIp())
    }

    @Test
    fun `currentEndpointPort extracts port correctly`() {
        val config = createTestConfig(port = 443)

        val port = config.currentEndpointPort()

        assertEquals(443L, port)
    }

    @Test
    fun `serverIp extracts host correctly`() {
        val config = createTestConfig(port = 443)

        val host = config.serverIp()

        assertNotNull(host)
        assertEquals("1.2.3.4", host)
    }

    @Test
    fun `replacingEndpointPort with no peers returns same config`() {
        val configString = """
            [Interface]
            PrivateKey = cG8QmezwBwf5HkiLGHhc/LQu/E2Ee6z3MR8t0s9nXUE=
            Address = 10.11.12.1/32
        """.trimIndent()

        val config = Config.parse(BufferedReader(StringReader(configString)))

        val updatedConfig = config.replacingEndpointPort(51820)

        assertEquals(config, updatedConfig)
    }

    private fun createTestConfig(port: Long): Config {
        val configString = """
            [Interface]
            PrivateKey = cG8QmezwBwf5HkiLGHhc/LQu/E2Ee6z3MR8t0s9nXUE=
            Address = 10.11.12.1/32

            [Peer]
            PublicKey = HIgo9xNzJMWLKASShiTqIybxZ0U3wGLiUeJ1PKf8ykw=
            Endpoint = 1.2.3.4:$port
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()

        return Config.parse(BufferedReader(StringReader(configString)))
    }
}
