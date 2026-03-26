/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.common.utils.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Inet4Address

@RunWith(AndroidJUnit4::class)
class NetworkExtensionsTest {

    @Test
    fun `test isCGNAT`() {
        // cover all CGNAT range
        for (octet2 in 64..127) {
            for (octet3 in 0..255) {
                for (octet4 in 0..255) {
                    assertTrue(Inet4Address.getByName("100.$octet2.$octet3.$octet4").isCGNATed())
                }
            }
        }

        // previous and next IP address
        assertFalse(Inet4Address.getByName("100.63.255.255").isCGNATed())
        assertFalse(Inet4Address.getByName("100.128.0.0").isCGNATed())
    }
}
