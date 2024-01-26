/*
 * Copyright © 2017-2021 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.config

import com.wireguard.config.Attribute.Companion.join
import com.wireguard.config.Attribute.Companion.parse
import com.wireguard.config.Attribute.Companion.split
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyFormatException
import java.lang.NumberFormatException
import java.lang.StringBuilder

/**
 * Represents the configuration for a WireGuard peer (a [Peer] block). Peers must have a public key,
 * and may optionally have several other attributes.
 *
 *
 * Instances of this class are immutable.
 */
class Peer private constructor(val builder: Builder) { // The collection is already immutable.
    /**
     * Returns the peer's set of allowed IPs.
     *
     * @return the set of allowed IPs
     */
    val allowedIps: Set<InetNetwork> = mutableSetOf<InetNetwork>().apply {
        // Defensively copy to ensure immutability even if the Builder is reused.
        this.addAll(builder.allowedIps)
    }

    /**
     * Returns the peer's endpoint.
     *
     * @return the endpoint, or `Optional.empty()` if none is configured
     */
    val endpoint: InetEndpoint? = builder.endpoint

    /**
     * @return the peers name, null if not configured
     */
    val name: String? = builder.name

    /**
     * @return the peers location, null if not configured
     */
    val location: String? = builder.location

    /**
     * Returns the peer's persistent keepalive.
     *
     * @return the persistent keepalive, or `Optional.empty()` if none is configured
     */
    val persistentKeepalive: Int? = builder.persistentKeepalive

    /**
     * Returns the peer's pre-shared key.
     *
     * @return the pre-shared key, or `Optional.empty()` if none is configured
     */
    val preSharedKey: Key? = builder.preSharedKey

    /**
     * Returns the peer's public key.
     *
     * @return the public key
     */
    val publicKey: Key = builder.publicKey!!
    override fun equals(obj: Any?): Boolean {
        if (obj !is Peer) return false
        return allowedIps == obj.allowedIps && endpoint == obj.endpoint && persistentKeepalive == obj.persistentKeepalive &&
            preSharedKey == obj.preSharedKey && publicKey == obj.publicKey
    }

    override fun hashCode(): Int {
        var hash = 1
        hash = 31 * hash + allowedIps.hashCode()
        hash = 31 * hash + endpoint.hashCode()
        hash = 31 * hash + persistentKeepalive.hashCode()
        hash = 31 * hash + preSharedKey.hashCode()
        hash = 31 * hash + publicKey.hashCode()
        return hash
    }

    /**
     * Converts the `Peer` into a string suitable for debugging purposes. The `Peer` is
     * identified by its public key and (if known) its endpoint.
     *
     * @return a concise single-line identifier for the `Peer`
     */
    override fun toString(): String {
        val sb = StringBuilder("(Peer ")
        sb.append(publicKey.toBase64())
        endpoint?.let { ep: InetEndpoint -> sb.append(" @").append(ep) }
        sb.append(')')
        return sb.toString()
    }

    /**
     * Converts the `Peer` into a string suitable for inclusion in a `wg-quick`
     * configuration file.
     *
     * @return the `Peer` represented as a series of "Key = Value" lines
     */
    fun toWgQuickString(): String {
        val sb = StringBuilder()
        if (allowedIps.isNotEmpty()) sb.append("AllowedIPs = ").append(join(allowedIps)).append('\n')
        endpoint?.let { ep: InetEndpoint? -> sb.append("Endpoint = ").append(ep).append('\n') }
        name?.let { ep: String? -> sb.append("Name = ").append(ep).append('\n') }
        location?.let { ep: String? -> sb.append("Location = ").append(ep).append('\n') }
        persistentKeepalive?.let { pk: Int? ->
            sb.append("PersistentKeepalive = ").append(pk).append('\n')
        }
        preSharedKey?.let { psk: Key ->
            sb.append("PreSharedKey = ").append(psk.toBase64()).append('\n')
        }
        sb.append("PublicKey = ").append(publicKey.toBase64()).append('\n')
        return sb.toString()
    }

    /**
     * Serializes the `Peer` for use with the WireGuard cross-platform userspace API. Note
     * that not all attributes are included in this representation.
     *
     * @return the `Peer` represented as a series of "key=value" lines
     */
    fun toWgUserspaceString(): String {
        val sb = StringBuilder()
        // The order here is important: public_key signifies the beginning of a new peer.
        sb.append("public_key=").append(publicKey.toHex()).append('\n')
        for (allowedIp in allowedIps) sb.append("allowed_ip=").append(allowedIp).append('\n')
        endpoint?.getResolved()?.let {
            sb.append("endpoint=").append(it).append('\n')
        }
        persistentKeepalive?.let { pk: Int? ->
            sb.append("persistent_keepalive_interval=").append(pk).append('\n')
        }
        preSharedKey?.let { psk: Key ->
            sb.append("preshared_key=").append(psk.toHex()).append('\n')
        }
        return sb.toString()
    }

    class Builder {
        // Defaults to an empty set.
        val allowedIps: MutableSet<InetNetwork> = mutableSetOf()

        // Defaults to not present.
        var endpoint: InetEndpoint? = null

        var name: String? = null

        var location: String? = null

        // Defaults to not present.
        var persistentKeepalive: Int? = null

        // Defaults to not present.
        var preSharedKey: Key? = null

        // No default; must be provided before building.
        var publicKey: Key? = null

        fun addAllowedIp(allowedIp: InetNetwork): Builder {
            allowedIps.add(allowedIp)
            return this
        }

        fun addAllowedIps(allowedIps: Collection<InetNetwork>): Builder {
            this.allowedIps.addAll(allowedIps)
            return this
        }

        @Throws(BadConfigException::class)
        fun build(): Peer {
            if (publicKey == null) {
                throw BadConfigException(
                    BadConfigException.Section.PEER,
                    BadConfigException.Location.PUBLIC_KEY,
                    BadConfigException.Reason.MISSING_ATTRIBUTE,
                    null,
                )
            }
            return Peer(this)
        }

        @Throws(BadConfigException::class)
        fun parseAllowedIPs(allowedIps: CharSequence): Builder {
            return try {
                for (allowedIp in split(allowedIps)) addAllowedIp(
                    InetNetwork.parse(
                        allowedIp,
                    ),
                )
                this
            } catch (e: ParseException) {
                throw BadConfigException(
                    BadConfigException.Section.PEER,
                    BadConfigException.Location.ALLOWED_IPS,
                    e,
                )
            }
        }

        @Throws(BadConfigException::class)
        fun parseEndpoint(endpoint: String): Builder {
            return try {
                setEndpoint(InetEndpoint.parse(endpoint))
            } catch (e: ParseException) {
                throw BadConfigException(
                    BadConfigException.Section.PEER,
                    BadConfigException.Location.ENDPOINT,
                    e,
                )
            }
        }

        @Throws(BadConfigException::class)
        fun parsePersistentKeepalive(persistentKeepalive: String): Builder {
            return try {
                setPersistentKeepalive(persistentKeepalive.toInt())
            } catch (e: NumberFormatException) {
                throw BadConfigException(
                    BadConfigException.Section.PEER,
                    BadConfigException.Location.PERSISTENT_KEEPALIVE,
                    persistentKeepalive,
                    e,
                )
            }
        }

        @Throws(BadConfigException::class)
        fun parsePreSharedKey(preSharedKey: String): Builder {
            return try {
                setPreSharedKey(Key.fromBase64(preSharedKey))
            } catch (e: KeyFormatException) {
                throw BadConfigException(
                    BadConfigException.Section.PEER,
                    BadConfigException.Location.PRE_SHARED_KEY,
                    e,
                )
            }
        }

        @Throws(BadConfigException::class)
        fun parsePublicKey(publicKey: String): Builder {
            return try {
                setPublicKey(Key.fromBase64(publicKey))
            } catch (e: KeyFormatException) {
                throw BadConfigException(
                    BadConfigException.Section.PEER,
                    BadConfigException.Location.PUBLIC_KEY,
                    e,
                )
            }
        }

        fun setEndpoint(endpoint: InetEndpoint): Builder {
            this.endpoint = endpoint
            return this
        }

        fun setName(name: String): Builder {
            this.name = name
            return this
        }

        fun setLocation(location: String): Builder {
            this.location = location
            return this
        }

        @Throws(BadConfigException::class)
        fun setPersistentKeepalive(persistentKeepalive: Int): Builder {
            if (persistentKeepalive < 0 || persistentKeepalive > MAX_PERSISTENT_KEEPALIVE) {
                throw BadConfigException(
                    BadConfigException.Section.PEER,
                    BadConfigException.Location.PERSISTENT_KEEPALIVE,
                    BadConfigException.Reason.INVALID_VALUE,
                    persistentKeepalive.toString(),
                )
            }
            this.persistentKeepalive = if (persistentKeepalive == 0) null else persistentKeepalive
            return this
        }

        fun setPreSharedKey(preSharedKey: Key): Builder {
            this.preSharedKey = preSharedKey
            return this
        }

        fun setPublicKey(publicKey: Key): Builder {
            this.publicKey = publicKey
            return this
        }

        companion object {
            // See wg(8)
            private const val MAX_PERSISTENT_KEEPALIVE = 65535
        }
    }

    companion object {
        /**
         * Parses an series of "KEY = VALUE" lines into a `Peer`. Throws [ParseException] if
         * the input is not well-formed or contains unknown attributes.
         *
         * @param lines an iterable sequence of lines, containing at least a public key attribute
         * @return a `Peer` with all of its attributes set from `lines`
         */
        @Throws(BadConfigException::class)
        fun parse(lines: Iterable<CharSequence>): Peer {
            val builder = Builder()
            for (line in lines) {
                val attribute: Attribute = parse(
                    line,
                ) ?: throw BadConfigException(
                    BadConfigException.Section.PEER,
                    BadConfigException.Location.TOP_LEVEL,
                    BadConfigException.Reason.SYNTAX_ERROR,
                    line,
                )

                when (attribute.key.lowercase()) {
                    "allowedips" -> builder.parseAllowedIPs(attribute.value)
                    "endpoint" -> builder.parseEndpoint(attribute.value)
                    "name" -> builder.setName(attribute.value)
                    "location" -> builder.setLocation(attribute.value)
                    "persistentkeepalive" -> builder.parsePersistentKeepalive(attribute.value)
                    "presharedkey" -> builder.parsePreSharedKey(attribute.value)
                    "publickey" -> builder.parsePublicKey(attribute.value)
                    else -> throw BadConfigException(
                        BadConfigException.Section.PEER,
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
