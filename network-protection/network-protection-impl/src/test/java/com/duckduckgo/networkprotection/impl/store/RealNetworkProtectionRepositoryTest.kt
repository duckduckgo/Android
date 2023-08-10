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

import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository.ServerDetails
import com.duckduckgo.networkprotection.store.NetworkProtectionPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealNetworkProtectionRepositoryTest {
    @Mock
    private lateinit var networkProtectionPrefs: NetworkProtectionPrefs
    private lateinit var testee: RealNetworkProtectionRepository

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealNetworkProtectionRepository(networkProtectionPrefs)

        whenever(networkProtectionPrefs.getString("wg_server_name", null)).thenReturn("expected_server_name")
        whenever(networkProtectionPrefs.getString("wg_server_ip", null)).thenReturn("expected_ip")
        whenever(networkProtectionPrefs.getString("wg_server_location", null)).thenReturn("expected_location")
        whenever(networkProtectionPrefs.getString("wg_private_key", null)).thenReturn("expected_private_key")
        whenever(networkProtectionPrefs.getLong("wg_server_enable_time", -1L)).thenReturn(123124312312L)
    }

    @Test
    fun whenGettingPrivateKeyThenGetStringFromPrefs() {
        assertEquals("expected_private_key", testee.privateKey)
    }

    @Test
    fun whenSettingPrivateKeyWithValueThenPutStringInPrefsAndSetLastUpdate() {
        testee.privateKey = "privateKey"

        verify(networkProtectionPrefs).putString("wg_private_key", "privateKey")
        verify(networkProtectionPrefs).putLong(eq("wg_private_key_last_update"), anyLong())
    }

    @Test
    fun whenSettingPrivateKeyNullThenClearPrivateKeyAndLastUpdate() {
        testee.privateKey = null

        verify(networkProtectionPrefs).putString("wg_private_key", null)
        verify(networkProtectionPrefs).putLong("wg_private_key_last_update", -1L)
    }

    @Test
    fun whenGettingEnabledTimeMillisThenGetLongFromPrefs() {
        assertEquals(123124312312L, testee.enabledTimeInMillis)
    }

    @Test
    fun whenSettingEnabledTimeMillisThenPutLongInPrefs() {
        testee.enabledTimeInMillis = 12243235423453L

        verify(networkProtectionPrefs).putLong("wg_server_enable_time", 12243235423453L)
    }

    @Test
    fun whenGettingServerDetailsThenGetServerDetailsFromPrefs() {
        assertEquals(
            ServerDetails(
                serverName = "expected_server_name",
                ipAddress = "expected_ip",
                location = "expected_location",
            ),
            testee.serverDetails,
        )
    }

    @Test
    fun whenSettingServerDetailsThenPutLongInPrefs() {
        testee.serverDetails = ServerDetails(
            serverName = "expected_server_name",
            ipAddress = "expected_ip",
            location = "expected_location",
        )

        verify(networkProtectionPrefs).putString("wg_server_ip", "expected_ip")
        verify(networkProtectionPrefs).putString("wg_server_location", "expected_location")
    }

    @Test
    fun whenServerDetailsIsSetToNullThenSetIpAndLocationToNull() {
        testee.serverDetails = null

        verify(networkProtectionPrefs).putString("wg_server_ip", null)
        verify(networkProtectionPrefs).putString("wg_server_location", null)
    }

    @Test
    fun whenBothIpAndLocationAreNullThenServerDetailsReturnNull() {
        whenever(networkProtectionPrefs.getString("wg_server_ip", null)).thenReturn(null)
        whenever(networkProtectionPrefs.getString("wg_server_location", null)).thenReturn(null)

        assertNull(testee.serverDetails)
    }

    @Test
    fun whenIpIsNullThenServerDetailsIpIsNull() {
        whenever(networkProtectionPrefs.getString("wg_server_ip", null)).thenReturn(null)

        assertEquals(
            ServerDetails(
                serverName = "expected_server_name",
                ipAddress = null,
                location = "expected_location",
            ),
            testee.serverDetails,
        )
    }

    @Test
    fun whenLocationIsNullThenServerDetailsLocationIsNull() {
        whenever(networkProtectionPrefs.getString("wg_server_location", null)).thenReturn(null)

        assertEquals(
            ServerDetails(
                serverName = "expected_server_name",
                ipAddress = "expected_ip",
                location = null,
            ),
            testee.serverDetails,
        )
    }

    @Test
    fun whenServerNameIsNullThenServerDetailsNameIsNull() {
        whenever(networkProtectionPrefs.getString("wg_server_name", null)).thenReturn(null)

        assertEquals(
            ServerDetails(
                serverName = null,
                ipAddress = "expected_ip",
                location = "expected_location",
            ),
            testee.serverDetails,
        )
    }

    @Test
    fun whenClearStoreThenClearStore() {
        networkProtectionPrefs.clear()

        verify(networkProtectionPrefs).clear()
    }
}
