/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.config

import logcat.logcat
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.math.floor
import kotlin.math.ln

data class CIDR(
    val address: InetAddress,
    val prefix: Int,
) : Comparable<CIDR> {

    val start: InetAddress?
        get() = (this.address.toLong() and prefix2mask(this.prefix)).toInetAddress()

    val end: InetAddress?
        get() = ((this.address.toLong() and prefix2mask(this.prefix)) + (1L shl (32 - this.prefix)) - 1).toInetAddress()

    override fun toString(): String {
        return address.hostAddress + "/" + prefix + "=" + start!!.hostAddress + "..." + end!!.hostAddress
    }

    @Suppress("NAME_SHADOWING")
    override operator fun compareTo(other: CIDR): Int {
        val cidrAsLong = this.address.toLong()
        val otherCidrAsLong = other.address.toLong()
        return cidrAsLong.compareTo(otherCidrAsLong)
    }

    companion object {
        @Throws(UnknownHostException::class)
        fun createFrom(
            start: InetAddress,
            end: InetAddress,
        ): List<CIDR> {
            val listResult: MutableList<CIDR> = ArrayList()

            logcat { "toCIDR(" + start.hostAddress + "," + end.hostAddress + ")" }

            var from = start.toLong()
            val to = end.toLong()
            while (to >= from) {
                var prefix: Byte = 32
                while (prefix > 0) {
                    val mask = prefix2mask(prefix - 1)
                    if ((from and mask) != from) break
                    prefix--
                }

                val max = (32 - floor(ln((to - from + 1).toDouble()) / ln(2.0))).toInt().toByte()
                if (prefix < max) prefix = max

                listResult.add(CIDR(from.toInetAddress()!!, prefix.toInt()))

                from += (1u shl (32 - prefix)).toLong()
            }

            for (cidr in listResult) {
                logcat { cidr.toString() }
            }

            return listResult
        }
    }
}

private fun prefix2mask(bits: Int): Long {
    return (-0x100000000L shr bits) and 0xFFFFFFFFL
}

private fun Long.toInetAddress(): InetAddress? {
    var addr = this
    try {
        val b = ByteArray(4)
        for (i in b.indices.reversed()) {
            b[i] = (addr and 0xFFL).toByte()
            addr = addr shr 8
        }
        return InetAddress.getByAddress(b)
    } catch (ignore: UnknownHostException) {
        return null
    }
}

internal fun InetAddress.minus1(): InetAddress? {
    return (this.toLong() - 1).toInetAddress()
}

internal fun InetAddress.plus1(): InetAddress? {
    return (this.toLong() + 1).toInetAddress()
}

internal fun InetAddress.toLong(): Long {
    val addr = this
    var result: Long = 0
    for (b in addr.address) result = result shl 8 or (b.toInt() and 0xFF).toLong()
    return result
}
