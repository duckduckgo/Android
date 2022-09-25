/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.processor.tcp.hostname

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsMessageDetectorTest {

    private val testee = TlsMessageDetector()

    @Test
    fun whenByteArrayIsEmptyThenIsNotTlsMessage() {
        assertFalse(testee.isTlsMessage(emptyByteArray()))
    }

    @Test
    fun whenByteArrayFirstByteIsCipherSpecChangeThenIsTlsMessage() {
        assertTrue(testee.isTlsMessage(cipherChangeByteArray()))
    }

    @Test
    fun whenByteArrayFirstByteIsAlertThenIsTlsMessage() {
        assertTrue(testee.isTlsMessage(alertByteArray()))
    }

    @Test
    fun whenByteArrayFirstByteIsHandshakeThenIsTlsMessage() {
        assertTrue(testee.isTlsMessage(handshakeByteArray()))
    }

    @Test
    fun whenByteArrayFirstByteIsApplicationThenIsTlsMessage() {
        assertTrue(testee.isTlsMessage(applicationByteArray()))
    }

    @Test
    fun whenByteArrayFirstByteIsHeartbeatThenIsTlsMessage() {
        assertTrue(testee.isTlsMessage(heartbeatByteArray()))
    }

    @Test
    fun whenByteArrayFirstByteIsNotKnownConstantThenIsNotTlsMessage() {
        assertFalse(testee.isTlsMessage(invalidTslByteArray()))
    }

    private fun emptyByteArray() = ByteArray(0)
    private fun invalidTslByteArray() = ByteArray(1).also { it[0] = 50 }

    private fun cipherChangeByteArray() = ByteArray(1).also { it[0] = TlsMessageDetector.CONTENT_TYPE_CHANGE_CIPHER_SPEC.toByte() }
    private fun alertByteArray() = ByteArray(1).also { it[0] = TlsMessageDetector.CONTENT_TYPE_ALERT.toByte() }
    private fun handshakeByteArray() = ByteArray(1).also { it[0] = TlsMessageDetector.CONTENT_TYPE_HANDSHAKE.toByte() }
    private fun applicationByteArray() = ByteArray(1).also { it[0] = TlsMessageDetector.CONTENT_TYPE_APPLICATION.toByte() }
    private fun heartbeatByteArray() = ByteArray(1).also { it[0] = TlsMessageDetector.CONTENT_TYPE_HEARTBEAT.toByte() }
}
