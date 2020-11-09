/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.processor.tcp.tracker

import org.junit.Assert.*

import org.junit.Before
import org.junit.Test
import java.net.InetAddress

class LocalIpAddressDetectorTest {

    private val testee: LocalIpAddressDetector = LocalIpAddressDetector()

    @Test
    fun whenAddressSmallestIn192_168RangeThenIsLocal() {
        assertLocal("192.168.0.0")
    }

    @Test
    fun whenAddressLargestIn192_168RangeThenIsLocal() {
        assertLocal("192.168.255.255")
    }

    @Test
    fun whenAddressOutwithPrivatePartIn192RangeThenIsNotLocal() {
        assertNotLocal("192.0.0.1")
    }

    @Test
    fun whenAddressSmallestIn10RangeThenIsLocal() {
        assertLocal("10.0.0.0")
    }

    @Test
    fun whenAddressLargestIn10RangeThenIsLocal() {
        assertLocal("10.255.255.255")
    }

    @Test
    fun whenAddressSmallestIn172RangeThenIsLocal() {
        assertLocal("172.16.0.0")
    }

    @Test
    fun whenAddressLargestIn172RangeThenIsLocal() {
        assertLocal("172.31.255.255")
    }

    @Test
    fun whenAddressOutwithPrivate172RangeThenIsNotLocal() {
        assertNotLocal("172.32.0.0")
    }

    private fun assertLocal(address: String) {
        assertTrue("$address is not local", testee.isLocalAddress(address.toInet()))
    }

    private fun assertNotLocal(address: String) {
        assertFalse("$address is local", testee.isLocalAddress(address.toInet()))
    }

    private fun String.toInet(): InetAddress {
        return InetAddress.getByName(this)
    }
}