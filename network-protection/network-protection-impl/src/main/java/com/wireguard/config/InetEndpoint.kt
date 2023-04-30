/*
 * Copyright © 2017-2021 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.config

import java.net.*
import java.util.*
import java.util.regex.Pattern

/**
 * An external endpoint (host and port) used to connect to a WireGuard [Peer].
 *
 *
 * Instances of this class are externally immutable.
 */
class InetEndpoint private constructor(
    val host: String,
    private val isResolved: Boolean,
    val port: Int,
) {
    private val lock = Any()
    private var lastResolutionInMillis = 0L
    private var resolved: InetEndpoint? = null
    override fun equals(obj: Any?): Boolean {
        if (obj !is InetEndpoint) return false
        return host == obj.host && port == obj.port
    }

    /**
     * Generate an `InetEndpoint` instance with the same port and the host resolved using DNS
     * to a numeric address. If the host is already numeric, the existing instance may be returned.
     * Because this function may perform network I/O, it must not be called from the main thread.
     *
     * @return the resolved endpoint, or [Optional.empty]
     */
    fun getResolved(): InetEndpoint? {
        if (isResolved) return this
        synchronized(lock) {
            // TODO(zx2c4): Implement a real timeout mechanism using DNS TTL
            if (System.currentTimeMillis() - lastResolutionInMillis > 1) {
                try {
                    // Prefer v4 endpoints over v6 to work around DNS64 and IPv6 NAT issues.
                    val candidates = InetAddress.getAllByName(
                        host,
                    )
                    var address = candidates[0]
                    for (candidate in candidates) {
                        if (candidate is Inet4Address) {
                            address = candidate
                            break
                        }
                    }
                    resolved = InetEndpoint(address.hostAddress, true, port)
                    lastResolutionInMillis = System.currentTimeMillis()
                } catch (e: UnknownHostException) {
                    resolved = null
                }
            }
            return resolved
        }
    }

    override fun hashCode(): Int {
        return host.hashCode() xor port
    }

    override fun toString(): String {
        val isBareIpv6 = isResolved && BARE_IPV6.matcher(
            host,
        ).matches()
        return (if (isBareIpv6) "[$host]" else host) + ':' + port
    }

    companion object {
        private val BARE_IPV6 = Pattern.compile("^[^\\[\\]]*:[^\\[\\]]*")
        private val FORBIDDEN_CHARACTERS = Pattern.compile("[/?#]")

        @JvmStatic
        @Throws(ParseException::class)
        fun parse(endpoint: String): InetEndpoint {
            if (FORBIDDEN_CHARACTERS.matcher(endpoint).find()) {
                throw ParseException(
                    InetEndpoint::class.java,
                    endpoint,
                    "Forbidden characters",
                )
            }
            val uri: URI = try {
                URI("wg://$endpoint")
            } catch (e: URISyntaxException) {
                throw ParseException(InetEndpoint::class.java, endpoint, null, e)
            }
            if (uri.port < 0 || uri.port > 65535) {
                throw ParseException(
                    InetEndpoint::class.java,
                    endpoint,
                    "Missing/invalid port number",
                )
            }
            return try {
                InetAddresses.parse(uri.host)
                // Parsing ths host as a numeric address worked, so we don't need to do DNS lookups.
                InetEndpoint(uri.host, true, uri.port)
            } catch (ignored: ParseException) {
                // Failed to parse the host as a numeric address, so it must be a DNS hostname/FQDN.
                InetEndpoint(uri.host, false, uri.port)
            }
        }
    }
}
