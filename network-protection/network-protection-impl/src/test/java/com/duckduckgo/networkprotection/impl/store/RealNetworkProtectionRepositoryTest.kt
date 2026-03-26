/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.store

import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.networkprotection.store.NetworkProtectionPrefs
import com.duckduckgo.networkprotection.store.RealNetworkProtectionPrefs
import com.wireguard.config.Config
import com.wireguard.crypto.KeyPair
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations
import java.io.BufferedReader
import java.io.StringReader

class RealNetworkProtectionRepositoryTest {
    private lateinit var networkProtectionPrefs: NetworkProtectionPrefs
    private val keys = KeyPair()

    private val wgQuickConfig = """
        [Interface]
        Address = 1.1.1.2/32
        DNS = 1.1.1.1
        MTU = 1280
        PrivateKey = ${keys.privateKey.toBase64()}

        [Peer]
        AllowedIPs = 0.0.0.0/0, 0.0.0.0/5, 8.0.0.0/7
        Endpoint = 1.1.1.1:443
        Name = expected_server_name
        Location = expected_location
        PublicKey = ${keys.publicKey.toBase64()}
    """.trimIndent()
    private val config = Config.parse(BufferedReader(StringReader(wgQuickConfig)))

    private lateinit var testee: RealNetworkProtectionRepository

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        networkProtectionPrefs = RealNetworkProtectionPrefs(FakeSharedPreferencesProvider())
        testee = RealNetworkProtectionRepository(networkProtectionPrefs)
    }

    @Test
    fun whenNoEnabledTimeMillisThenReturnDefaultValue() {
        assertEquals(-1, testee.enabledTimeInMillis)
    }

    @Test
    fun whenSettingEnabledTimeMillisThenPutLongInPrefs() {
        testee.enabledTimeInMillis = 12243235423453L

        assertEquals(12243235423453L, networkProtectionPrefs.getLong("wg_server_enable_time", -1))
    }
}
