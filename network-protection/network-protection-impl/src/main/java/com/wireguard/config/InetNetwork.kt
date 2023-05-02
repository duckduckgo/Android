/*
 * Copyright © 2017-2021 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.config

import java.lang.NumberFormatException
import java.net.Inet4Address
import java.net.InetAddress

/**
 * An Internet network, denoted by its address and netmask
 *
 *
 * Instances of this class are immutable.
 */
class InetNetwork private constructor(
    val address: InetAddress,
    val mask: Int,
) {
    override fun equals(obj: Any?): Boolean {
        if (obj !is InetNetwork) return false
        val other = obj
        return address == other.address && mask == other.mask
    }

    override fun hashCode(): Int {
        return address.hashCode() xor mask
    }

    override fun toString(): String {
        return address.hostAddress!! + '/' + mask
    }

    companion object {
        @JvmStatic
        @Throws(ParseException::class)
        fun parse(network: String): InetNetwork {
            val slash = network.lastIndexOf('/')
            val maskString: String
            val rawMask: Int
            val rawAddress: String
            if (slash >= 0) {
                maskString = network.substring(slash + 1)
                rawMask = try {
                    maskString.toInt(10)
                } catch (ignored: NumberFormatException) {
                    throw ParseException(Int::class.java, maskString)
                }
                rawAddress = network.substring(0, slash)
            } else {
                maskString = ""
                rawMask = -1
                rawAddress = network
            }
            val address = InetAddresses.parse(rawAddress)
            val maxMask = if (address is Inet4Address) 32 else 128
            if (rawMask > maxMask) {
                throw ParseException(
                    InetNetwork::class.java,
                    maskString,
                    "Invalid network mask",
                )
            }
            val mask = if (rawMask >= 0) rawMask else maxMask
            return InetNetwork(address, mask)
        }
    }
}
