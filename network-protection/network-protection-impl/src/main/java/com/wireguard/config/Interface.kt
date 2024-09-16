/*
 * Copyright © 2017-2021 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.config

import com.wireguard.config.Attribute.Companion.join
import com.wireguard.config.Attribute.Companion.parse
import com.wireguard.config.Attribute.Companion.split
import com.wireguard.config.InetAddresses.isHostname
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyFormatException
import com.wireguard.crypto.KeyPair
import java.lang.NumberFormatException
import java.lang.StringBuilder
import java.net.InetAddress

/**
 * Represents the configuration for a WireGuard interface (an [Interface] block). Interfaces must
 * have a private key (used to initialize a `KeyPair`), and may optionally have several other
 * attributes.
 *
 *
 * Instances of this class are immutable.
 */
class Interface private constructor(val builder: Builder) { // The collection is already immutable.
    /**
     * Returns the set of IP addresses assigned to the interface.
     *
     * @return a set of [InetNetwork]s
     */
    val addresses: Set<InetNetwork> = mutableSetOf<InetNetwork>().apply {
        // Defensively copy to ensure immutability even if the Builder is reused.
        this.addAll(builder.addresses)
    }

    /**
     * Returns the set of routes to be configured in the interface
     *
     * @return a set of [InetNetwork]es
     */
    val routes: Set<InetNetwork> = mutableSetOf<InetNetwork>().apply {
        // Defensively copy to ensure immutability even if the Builder is reused.
        this.addAll(builder.routes)
    }

    /**
     * Returns the set of DNS servers associated with the interface.
     *
     * @return a set of [InetAddress]es
     */
    val dnsServers: Set<InetAddress> = mutableSetOf<InetAddress>().apply {
        // Defensively copy to ensure immutability even if the Builder is reused.
        this.addAll(builder.dnsServers)
    }

    /**
     * Returns the set of DNS search domains associated with the interface.
     *
     * @return a set of strings
     */
    val dnsSearchDomains: Set<String> = mutableSetOf<String>().apply {
        // Defensively copy to ensure immutability even if the Builder is reused.
        this.addAll(builder.dnsSearchDomains)
    }

    /**
     * Returns the set of applications excluded from using the interface.
     *
     * @return a set of package names
     */
    val excludedApplications: Set<String> = mutableSetOf<String>().apply {
        // Defensively copy to ensure immutability even if the Builder is reused.
        this.addAll(builder.excludedApplications)
    }

    /**
     * Returns the set of applications included exclusively for using the interface.
     *
     * @return a set of package names
     */
    val includedApplications: Set<String> = mutableSetOf<String>().apply {
        // Defensively copy to ensure immutability even if the Builder is reused.
        this.addAll(builder.includedApplications)
    }

    /**
     * Returns the public/private key pair used by the interface.
     *
     * @return a key pair
     */
    val keyPair: KeyPair = builder.keyPair!!

    /**
     * Returns the UDP port number that the WireGuard interface will listen on.
     *
     * @return a UDP port number, or `Optional.empty()` if none is configured
     */
    val listenPort: Int? = builder.listenPort

    /**
     * Returns the MTU used for the WireGuard interface.
     *
     * @return the MTU, or `Optional.empty()` if none is configured
     */
    val mtu: Int? = builder.mtu
    override fun equals(obj: Any?): Boolean {
        if (obj !is Interface) return false
        return addresses == obj.addresses && dnsServers == obj.dnsServers && dnsSearchDomains == obj.dnsSearchDomains &&
            excludedApplications == obj.excludedApplications && includedApplications == obj.includedApplications && keyPair == obj.keyPair &&
            listenPort == obj.listenPort && mtu == obj.mtu
    }

    override fun hashCode(): Int {
        var hash = 1
        hash = 31 * hash + addresses.hashCode()
        hash = 31 * hash + dnsServers.hashCode()
        hash = 31 * hash + excludedApplications.hashCode()
        hash = 31 * hash + includedApplications.hashCode()
        hash = 31 * hash + keyPair.hashCode()
        hash = 31 * hash + listenPort.hashCode()
        hash = 31 * hash + mtu.hashCode()
        return hash
    }

    /**
     * Converts the `Interface` into a string suitable for debugging purposes. The `Interface` is identified by its public key and (if set) the port used for its UDP socket.
     *
     * @return A concise single-line identifier for the `Interface`
     */
    override fun toString(): String {
        val sb = StringBuilder("(Interface ")
        sb.append(keyPair.publicKey.toBase64())
        listenPort?.let { lp: Int? -> sb.append(" @").append(lp) }
        sb.append(')')
        return sb.toString()
    }

    /**
     * Converts the `Interface` into a string suitable for inclusion in a `wg-quick`
     * configuration file.
     *
     * @return The `Interface` represented as a series of "Key = Value" lines
     */
    fun toWgQuickString(): String {
        val sb = StringBuilder()
        if (addresses.isNotEmpty()) sb.append("Address = ").append(join(addresses)).append('\n')
        if (routes.isNotEmpty()) {
            sb.append("Routes = ").append(join(routes)).append('\n')
        }
        if (dnsServers.isNotEmpty()) {
            val dnsServerStrings = dnsServers.map { obj: InetAddress -> obj.hostAddress }.toMutableList()
            dnsServerStrings.addAll(dnsSearchDomains)
            sb.append("DNS = ").append(join(dnsServerStrings)).append('\n')
        }
        if (!excludedApplications.isEmpty()) {
            sb.append("ExcludedApplications = ")
                .append(join(excludedApplications)).append('\n')
        }
        if (!includedApplications.isEmpty()) {
            sb.append("IncludedApplications = ")
                .append(join(includedApplications)).append('\n')
        }
        listenPort?.let { lp: Int? -> sb.append("ListenPort = ").append(lp).append('\n') }
        mtu?.let { m: Int? -> sb.append("MTU = ").append(m).append('\n') }
        sb.append("PrivateKey = ").append(keyPair.privateKey.toBase64()).append('\n')
        return sb.toString()
    }

    /**
     * Serializes the `Interface` for use with the WireGuard cross-platform userspace API.
     * Note that not all attributes are included in this representation.
     *
     * @return the `Interface` represented as a series of "KEY=VALUE" lines
     */
    fun toWgUserspaceString(): String {
        val sb = StringBuilder()
        sb.append("private_key=").append(keyPair.privateKey.toHex()).append('\n')
        listenPort?.let { lp: Int? -> sb.append("listen_port=").append(lp).append('\n') }
        return sb.toString()
    }

    class Builder {
        // Defaults to an empty set.
        val addresses: MutableSet<InetNetwork> = linkedSetOf()

        // Defaults to an empty set.
        val routes: MutableSet<InetNetwork> = linkedSetOf()

        // Defaults to an empty set.
        val dnsServers: MutableSet<InetAddress> = linkedSetOf()

        // Defaults to an empty set.
        val dnsSearchDomains: MutableSet<String> = linkedSetOf()

        // Defaults to an empty set.
        val excludedApplications: MutableSet<String> = linkedSetOf()

        // Defaults to an empty set.
        val includedApplications: MutableSet<String> = linkedSetOf()

        // No default; must be provided before building.
        var keyPair: KeyPair? = null

        // Defaults to not present.
        var listenPort: Int? = null

        // Defaults to not present.
        var mtu: Int? = null
        fun addAddress(address: InetNetwork): Builder {
            addresses.add(address)
            return this
        }

        fun addAddresses(addresses: Collection<InetNetwork>): Builder {
            this.addresses.addAll(addresses)
            return this
        }

        fun addRoute(route: InetNetwork): Builder {
            routes.add(route)
            return this
        }

        fun addRoutes(routes: Collection<InetNetwork>): Builder {
            this.routes.addAll(routes)
            return this
        }

        fun addDnsServer(dnsServer: InetAddress): Builder {
            dnsServers.add(dnsServer)
            return this
        }

        fun addDnsServers(dnsServers: Collection<InetAddress>): Builder {
            this.dnsServers.addAll(dnsServers)
            return this
        }

        fun addDnsSearchDomain(dnsSearchDomain: String): Builder {
            dnsSearchDomains.add(dnsSearchDomain)
            return this
        }

        fun addDnsSearchDomains(dnsSearchDomains: Collection<String>): Builder {
            this.dnsSearchDomains.addAll(dnsSearchDomains)
            return this
        }

        @Throws(BadConfigException::class)
        fun build(): Interface {
            if (keyPair == null) {
                throw BadConfigException(
                    BadConfigException.Section.INTERFACE,
                    BadConfigException.Location.PRIVATE_KEY,
                    BadConfigException.Reason.MISSING_ATTRIBUTE,
                    null,
                )
            }
            if (includedApplications.isNotEmpty() && excludedApplications.isNotEmpty()) {
                throw BadConfigException(
                    BadConfigException.Section.INTERFACE,
                    BadConfigException.Location.INCLUDED_APPLICATIONS,
                    BadConfigException.Reason.INVALID_KEY,
                    null,
                )
            }
            return Interface(this)
        }

        fun excludeApplication(application: String): Builder {
            excludedApplications.add(application)
            return this
        }

        fun excludeApplications(applications: Collection<String>): Builder {
            excludedApplications.addAll(applications)
            return this
        }

        fun includeApplication(application: String): Builder {
            includedApplications.add(application)
            return this
        }

        fun includeApplications(applications: Collection<String>): Builder {
            includedApplications.addAll(applications)
            return this
        }

        @Throws(BadConfigException::class)
        fun parseAddresses(addresses: CharSequence): Builder {
            return try {
                for (address in split(addresses)) addAddress(
                    InetNetwork.parse(
                        address,
                    ),
                )
                this
            } catch (e: ParseException) {
                throw BadConfigException(
                    BadConfigException.Section.INTERFACE,
                    BadConfigException.Location.ADDRESS,
                    e,
                )
            }
        }

        @Throws(BadConfigException::class)
        fun parseRoutes(routes: CharSequence): Builder {
            return try {
                for (route in split(routes)) addRoute(
                    InetNetwork.parse(route),
                )
                this
            } catch (e: ParseException) {
                throw BadConfigException(
                    BadConfigException.Section.INTERFACE,
                    BadConfigException.Location.ROUTE,
                    e,
                )
            }
        }

        @Throws(BadConfigException::class)
        fun parseDnsServers(dnsServers: CharSequence): Builder {
            return try {
                for (dnsServer in split(dnsServers)) {
                    try {
                        addDnsServer(InetAddresses.parse(dnsServer))
                    } catch (e: ParseException) {
                        if (e.parsingClass != InetAddress::class.java || !isHostname(
                                dnsServer,
                            )
                        ) {
                            throw e
                        }
                        addDnsSearchDomain(dnsServer)
                    }
                }
                this
            } catch (e: ParseException) {
                throw BadConfigException(
                    BadConfigException.Section.INTERFACE,
                    BadConfigException.Location.DNS,
                    e,
                )
            }
        }

        fun parseExcludedApplications(apps: CharSequence): Builder {
            return excludeApplications(
                listOf(
                    *split(
                        apps,
                    ),
                ),
            )
        }

        fun parseIncludedApplications(apps: CharSequence): Builder {
            return includeApplications(
                listOf(
                    *split(
                        apps,
                    ),
                ),
            )
        }

        @Throws(BadConfigException::class)
        fun parseListenPort(listenPort: String): Builder {
            return try {
                setListenPort(listenPort.toInt())
            } catch (e: NumberFormatException) {
                throw BadConfigException(
                    BadConfigException.Section.INTERFACE,
                    BadConfigException.Location.LISTEN_PORT,
                    listenPort,
                    e,
                )
            }
        }

        @Throws(BadConfigException::class)
        fun parseMtu(mtu: String): Builder {
            return try {
                setMtu(mtu.toInt())
            } catch (e: NumberFormatException) {
                throw BadConfigException(
                    BadConfigException.Section.INTERFACE,
                    BadConfigException.Location.MTU,
                    mtu,
                    e,
                )
            }
        }

        @Throws(BadConfigException::class)
        fun parsePrivateKey(privateKey: String): Builder {
            return try {
                setKeyPair(KeyPair(Key.fromBase64(privateKey)))
            } catch (e: KeyFormatException) {
                throw BadConfigException(
                    BadConfigException.Section.INTERFACE,
                    BadConfigException.Location.PRIVATE_KEY,
                    e,
                )
            }
        }

        fun setKeyPair(keyPair: KeyPair): Builder {
            this.keyPair = keyPair
            return this
        }

        @Throws(BadConfigException::class)
        fun setListenPort(listenPort: Int): Builder {
            if (listenPort < MIN_UDP_PORT || listenPort > MAX_UDP_PORT) {
                throw BadConfigException(
                    BadConfigException.Section.INTERFACE,
                    BadConfigException.Location.LISTEN_PORT,
                    BadConfigException.Reason.INVALID_VALUE,
                    listenPort.toString(),
                )
            }
            this.listenPort = if (listenPort == 0) null else listenPort
            return this
        }

        @Throws(BadConfigException::class)
        fun setMtu(mtu: Int): Builder {
            if (mtu < 0) {
                throw BadConfigException(
                    BadConfigException.Section.INTERFACE,
                    BadConfigException.Location.LISTEN_PORT,
                    BadConfigException.Reason.INVALID_VALUE,
                    mtu.toString(),
                )
            }
            this.mtu = if (mtu == 0) null else mtu
            return this
        }
    }

    companion object {
        private const val MAX_UDP_PORT = 65535
        private const val MIN_UDP_PORT = 0

        /**
         * Parses an series of "KEY = VALUE" lines into an `Interface`. Throws
         * [ParseException] if the input is not well-formed or contains unknown attributes.
         *
         * @param lines An iterable sequence of lines, containing at least a private key attribute
         * @return An `Interface` with all of the attributes from `lines` set
         */
        @Throws(BadConfigException::class)
        fun parse(lines: Iterable<CharSequence>): Interface {
            val builder = Builder()
            for (line in lines) {
                val attribute: Attribute = parse(
                    line,
                ) ?: throw BadConfigException(
                    BadConfigException.Section.INTERFACE,
                    BadConfigException.Location.TOP_LEVEL,
                    BadConfigException.Reason.SYNTAX_ERROR,
                    line,
                )
                when (attribute.key.lowercase()) {
                    "address" -> builder.parseAddresses(attribute.value)
                    "routes" -> builder.parseRoutes(attribute.value)
                    "dns" -> builder.parseDnsServers(attribute.value)
                    "excludedapplications" -> builder.parseExcludedApplications(attribute.value)
                    "includedapplications" -> builder.parseIncludedApplications(attribute.value)
                    "listenport" -> builder.parseListenPort(attribute.value)
                    "mtu" -> builder.parseMtu(attribute.value)
                    "privatekey" -> builder.parsePrivateKey(attribute.value)
                    else -> throw BadConfigException(
                        BadConfigException.Section.INTERFACE,
                        BadConfigException.Location.TOP_LEVEL,
                        BadConfigException.Reason.UNKNOWN_ATTRIBUTE,
                        attribute.key,
                    )
                }
            }
            return builder.build()
        }
    }
}
