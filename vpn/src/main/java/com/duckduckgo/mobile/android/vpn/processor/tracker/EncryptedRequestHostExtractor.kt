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

package com.duckduckgo.mobile.android.vpn.processor.tracker

import timber.log.Timber
import kotlin.text.Charsets.US_ASCII


interface EncryptedRequestHostExtractor {
    fun extract(packet: ByteArray): String?
}

class ServerNameIndicationHeaderHostExtractor : EncryptedRequestHostExtractor {

    override fun extract(packet: ByteArray): String? {

        if (!isClientHelloProtocol(packet)) {
            Timber.v("Not a Client Hello packet")
            return null
        }

        return extractHostFromClientHelloSniHeader(packet)
    }

    private fun extractHostFromClientHelloSniHeader(packet: ByteArray): String? {
        // this skips the TLS header, time and Client Random - and starts with the session ID length
        var index = 43
        val sessionIdLength = packet[index].toInt() and 0xFF
        index++
        index += sessionIdLength

        val cipherSuitesLength = HigherOrderByte(packet[index]) + LowerOrderByte(packet[index + 1])
        index += 2
        index += cipherSuitesLength

        val compressionMethodLength = packet[index].toInt()
        index++
        index += compressionMethodLength

        val extensionsLength = HigherOrderByte(packet[index]) + LowerOrderByte(packet[index + 1])
        index += 2
        if (extensionsLength == 0) {
            return null
        }

        val sniHeaderStart = findServerNameIndicationHeaderStart(packet, index, extensionsLength)
        if (sniHeaderStart == -1) return null

        index = sniHeaderStart

        // skip 5 bytes for data sizes we don't need to know about
        index += 5

        val serverNameLength = HigherOrderByte(packet[index]) + LowerOrderByte(packet[index + 1])
        index += 2
        val serverNameBytes = ByteArray(serverNameLength)

        packet.copyInto(serverNameBytes, 0, index, index + serverNameLength)
        return String(serverNameBytes, US_ASCII)
    }

    private fun findServerNameIndicationHeaderStart(packet: ByteArray, extensionStartIndex: Int, extensionsLength: Int): Int {
        var extensionBytesSearched = 0
        var index = extensionStartIndex

        while (extensionBytesSearched < extensionsLength && index < packet.size) {
            Timber.i("Extensions length: %d, current index = %d, packet size=%d", extensionsLength, index, packet.size)

            val extensionTypeHigher = HigherOrderByte(packet[index])
            index++

            val extensionTypeLower = LowerOrderByte(packet[index])
            index++

            val extensionType = addHigherLowerOrderBytes(extensionTypeHigher, extensionTypeLower)

            // Server Name extension has type 00 00
            if (extensionType == 0) {
                Timber.i("Found extension type for SNI at index %d", index)
                return index
            } else {
                val extensionLengthHigher = HigherOrderByte(packet[index])
                index++

                val extensionLengthLower = LowerOrderByte(packet[index])
                index++

                val extensionLength = addHigherLowerOrderBytes(extensionLengthHigher, extensionLengthLower)

                // skip to next extension, if there is a next one
                index += extensionLength

                // record number of extension bytes search, which is the current extension length + 4 (2 bytes for type, 2 bytes for length)
                extensionBytesSearched += extensionLength + 4
            }
        }

        return -1
    }

    // based on https://www.ietf.org/rfc/rfc5246.txt
    private fun isClientHelloProtocol(packet: ByteArray): Boolean {
        if (packet.size < TLS_HEADER_SIZE) {
            return false
        }

        val contentType = packet[0].toInt()
        val tlsVersionMajor = packet[1].toInt()
        val tlsVersionMinor = packet[2].toInt()

        Timber.i("Got TLS version, major:%d, minor:%d. Content type=%d", tlsVersionMajor, tlsVersionMinor, contentType)

        if (tlsVersionMajor < 0x03) {
            Timber.v("TLS version wouldn't include a SNI header so no point in looking further for it")
            return false
        }

        if (contentType != 22) {
            Timber.v("Not a handshake packet; not going to find SNI header in here")
            return false
        }

        val handshakeLength = determineTlsHandshakeLength(packet)

        if (packet.size < handshakeLength + TLS_HEADER_SIZE) {
            Timber.w("TLS packet size unexpected. Handshake size reported %d is greater than total packet size %d", handshakeLength, packet.size)
            return false
        }

        val handshakeMessageType = packet[TLS_HEADER_SIZE]
        if (handshakeMessageType == 1.toByte()) {
            Timber.i("This is a ClientHello message")
            return true
        }

        return false
    }

    private fun determineTlsHandshakeLength(packet: ByteArray): Int {
        val lengthPart1 = HigherOrderByte(packet[3])
        val lengthPart2 = LowerOrderByte(packet[4])
        return addHigherLowerOrderBytes(lengthPart1, lengthPart2)
    }

    // first byte must be multiplied by 256 (or shl 8), then added to second
    private fun addHigherLowerOrderBytes(higherOrderByte: HigherOrderByte, lowerOrderByte: LowerOrderByte): Int {
        val higherInt = higherOrderByte.byte.toInt() shl 8 and 0xFF00
        val lowerInt = lowerOrderByte.byte.toInt() and 0x00FF
        return higherInt + lowerInt
    }

    companion object {
        private const val TLS_HEADER_SIZE = 5
    }
}

inline class HigherOrderByte(val byte: Byte) {

    operator fun plus(lowerOrderByte: LowerOrderByte): Int {
        return (this.byte.toInt() shl 8 and 0xFF00) + (lowerOrderByte.byte.toInt() and 0x00FF)
    }

}

inline class LowerOrderByte(val byte: Byte)