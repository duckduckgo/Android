/*
 * Copyright © 2017-2021 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.crypto

import com.wireguard.crypto.Curve25519.Companion.eval
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Represents a WireGuard public or private key. This class uses specialized constant-time base64
 * and hexadecimal codec implementations that resist side-channel attacks.
 *
 *
 * Instances of this class are immutable.
 */
class Key private constructor(key: ByteArray) {
    private val key: ByteArray = key.copyOf(key.size)
    override fun equals(obj: Any?): Boolean {
        if (obj === this) return true
        if (obj == null || obj.javaClass != javaClass) return false
        val other = obj as Key
        return MessageDigest.isEqual(key, other.key)
    } // Defensively copy to ensure immutability.

    /**
     * Returns the key as an array of bytes.
     *
     * @return an array of bytes containing the raw binary key
     */
    val bytes: ByteArray
        get() = // Defensively copy to ensure immutability.
            key.copyOf(key.size)

    override fun hashCode(): Int {
        var ret = 0
        for (i in 0 until key.size / 4) ret =
            ret xor (key[i * 4 + 0] shr 0) + (key[i * 4 + 1] shr 8) + (key[i * 4 + 2] shr 16) + (key[i * 4 + 3] shr 24)
        return ret
    }

    /**
     * Encodes the key to base64.
     *
     * @return a string containing the encoded key
     */
    fun toBase64(): String {
        val output = CharArray(Format.BASE64.length)
        var i = 0
        while (i < key.size / 3) {
            encodeBase64(key, i * 3, output, i * 4)
            ++i
        }
        val endSegment = byteArrayOf(
            key[i * 3],
            key[i * 3 + 1],
            0,
        )
        encodeBase64(endSegment, 0, output, i * 4)
        output[Format.BASE64.length - 1] = '='
        return String(output)
    }

    /**
     * Encodes the key to hexadecimal ASCII characters.
     *
     * @return a string containing the encoded key
     */
    fun toHex(): String {
        val output = CharArray(Format.HEX.length)
        for (i in key.indices) {
            output[i * 2] = (
                87 + (key[i] shr 4 and 0xf) +
                    ((key[i] shr 4 and 0xf) - 10 shr 8 and 38.inv())
                ).toChar()
            output[i * 2 + 1] = (
                87 + (key[i].and(0xf)) +
                    ((key[i].and(0xf)) - 10 shr 8 and 38.inv())
                ).toChar()
        }
        return String(output)
    }

    /**
     * The supported formats for encoding a WireGuard key.
     */
    enum class Format(val length: Int) {
        BASE64(44), BINARY(32), HEX(64)
    }

    companion object {
        /**
         * Decodes a single 4-character base64 chunk to an integer in constant time.
         *
         * @param src       an array of at least 4 characters in base64 format
         * @param srcOffset the offset of the beginning of the chunk in `src`
         * @return the decoded 3-byte integer, or some arbitrary integer value if the input was not
         * valid base64
         */
        private fun decodeBase64(src: CharArray, srcOffset: Int): Int {
            var `val` = 0
            for (i in 0..3) {
                val c = src[i + srcOffset]
                `val` = `val` or (
                    (
                        -1 +
                            ('A'.code - 1 - c.code and c.code - ('Z'.code + 1) ushr 8 and c.code - 64) +
                            ('a'.code - 1 - c.code and c.code - ('z'.code + 1) ushr 8 and c.code - 70) +
                            ('0'.code - 1 - c.code and c.code - ('9'.code + 1) ushr 8 and c.code + 5) +
                            ('+'.code - 1 - c.code and c.code - ('+'.code + 1) ushr 8 and 63) +
                            ('/'.code - 1 - c.code and c.code - ('/'.code + 1) ushr 8 and 64)
                        ) shl 18 - 6 * i
                    )
            }
            return `val`
        }

        /**
         * Encodes a single 4-character base64 chunk from 3 consecutive bytes in constant time.
         *
         * @param src        an array of at least 3 bytes
         * @param srcOffset  the offset of the beginning of the chunk in `src`
         * @param dest       an array of at least 4 characters
         * @param destOffset the offset of the beginning of the chunk in `dest`
         */
        private fun encodeBase64(
            src: ByteArray,
            srcOffset: Int,
            dest: CharArray,
            destOffset: Int,
        ) {
            val input = byteArrayOf(
                (src[srcOffset] ushr 2 and 63).toByte(),
                (src[srcOffset] shl 4 or (src[1 + srcOffset] and 0xff ushr 4) and 63).toByte(),
                (src[1 + srcOffset] shl 2 or (src[2 + srcOffset] and 0xff ushr 6) and 63).toByte(),
                (src[2 + srcOffset].and(63)).toByte(),
            )
            for (i in 0..3) {
                dest[i + destOffset] = (
                    (
                        (
                            input[i] + 'A'.code +
                                (25 - input[i] ushr 8 and 6)
                            ) -
                            (51 - input[i] ushr 8 and 75) -
                            (61 - input[i] ushr 8 and 15)
                        ) +
                        (62 - input[i] ushr 8 and 3)
                    ).toChar()
            }
        }

        /**
         * Decodes a WireGuard public or private key from its base64 string representation. This
         * function throws a [KeyFormatException] if the source string is not well-formed.
         *
         * @param str the base64 string representation of a WireGuard key
         * @return the decoded key encapsulated in an immutable container
         */
        @Throws(KeyFormatException::class)
        fun fromBase64(str: String): Key {
            val input = str.toCharArray()
            if (input.size != Format.BASE64.length || input[Format.BASE64.length - 1] != '=') {
                throw KeyFormatException(
                    Format.BASE64,
                    KeyFormatException.Type.LENGTH,
                )
            }
            val key = ByteArray(Format.BINARY.length)
            var i: Int
            var ret = 0
            i = 0
            while (i < key.size / 3) {
                val `val` = decodeBase64(input, i * 4)
                ret = ret or (`val` ushr 31)
                key[i * 3] = (`val` ushr 16 and 0xff).toByte()
                key[i * 3 + 1] = (`val` ushr 8 and 0xff).toByte()
                key[i * 3 + 2] = (`val` and 0xff).toByte()
                ++i
            }
            val endSegment = charArrayOf(
                input[i * 4],
                input[i * 4 + 1],
                input[i * 4 + 2],
                'A',
            )
            val `val` = decodeBase64(endSegment, 0)
            ret = ret or (`val` ushr 31 or (`val` and 0xff))
            key[i * 3] = (`val` ushr 16 and 0xff).toByte()
            key[i * 3 + 1] = (`val` ushr 8 and 0xff).toByte()
            if (ret != 0) throw KeyFormatException(Format.BASE64, KeyFormatException.Type.CONTENTS)
            return Key(key)
        }

        /**
         * Wraps a WireGuard public or private key in an immutable container. This function throws a
         * [KeyFormatException] if the source data is not the correct length.
         *
         * @param bytes an array of bytes containing a WireGuard key in binary format
         * @return the key encapsulated in an immutable container
         */
        @Throws(KeyFormatException::class)
        fun fromBytes(bytes: ByteArray): Key {
            if (bytes.size != Format.BINARY.length) {
                throw KeyFormatException(
                    Format.BINARY,
                    KeyFormatException.Type.LENGTH,
                )
            }
            return Key(bytes)
        }

        /**
         * Decodes a WireGuard public or private key from its hexadecimal string representation. This
         * function throws a [KeyFormatException] if the source string is not well-formed.
         *
         * @param str the hexadecimal string representation of a WireGuard key
         * @return the decoded key encapsulated in an immutable container
         */
        @Throws(KeyFormatException::class)
        fun fromHex(str: String): Key {
            val input = str.toCharArray()
            if (input.size != Format.HEX.length) {
                throw KeyFormatException(
                    Format.HEX,
                    KeyFormatException.Type.LENGTH,
                )
            }
            val key = ByteArray(Format.BINARY.length)
            var ret = 0
            for (i in key.indices) {
                var cNum0: Int
                var cAlpha: Int
                var cVal: Int
                var c: Int = input[i * 2].code
                var cNum: Int = c xor 48
                cNum0 = cNum - 10 ushr 8 and 0xff
                cAlpha = (c and 32.inv()) - 55
                var cAlpha0: Int = cAlpha - 10 xor cAlpha - 16 ushr 8 and 0xff
                ret = ret or ((cNum0 or cAlpha0) - 1 ushr 8)
                cVal = cNum0 and cNum or (cAlpha0 and cAlpha)
                val cAcc: Int = cVal * 16
                c = input[i * 2 + 1].code
                cNum = c xor 48
                cNum0 = cNum - 10 ushr 8 and 0xff
                cAlpha = (c and 32.inv()) - 55
                cAlpha0 = cAlpha - 10 xor cAlpha - 16 ushr 8 and 0xff
                ret = ret or ((cNum0 or cAlpha0) - 1 ushr 8)
                cVal = cNum0 and cNum or (cAlpha0 and cAlpha)
                key[i] = (cAcc or cVal).toByte()
            }
            if (ret != 0) throw KeyFormatException(Format.HEX, KeyFormatException.Type.CONTENTS)
            return Key(key)
        }

        /**
         * Generates a private key using the system's [SecureRandom] number generator.
         *
         * @return a well-formed random private key
         */
        fun generatePrivateKey(): Key {
            val secureRandom = SecureRandom()
            val privateKey = ByteArray(Format.BINARY.length)
            secureRandom.nextBytes(privateKey)
            privateKey[0] = (privateKey[0].and(248)).toByte()
            privateKey[31] = (privateKey[31].and(127)).toByte()
            privateKey[31] = (privateKey[31].or(64)).toByte()
            return Key(privateKey)
        }

        /**
         * Generates a public key from an existing private key.
         *
         * @param privateKey a private key
         * @return a well-formed public key that corresponds to the supplied private key
         */
        fun generatePublicKey(privateKey: Key): Key {
            val publicKey = ByteArray(Format.BINARY.length)
            eval(publicKey, 0, privateKey.bytes, null)
            return Key(publicKey)
        }
    }
}
