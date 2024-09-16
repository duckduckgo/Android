/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.vpn.network.api

interface VpnNetworkCallback {

    /**
     * Called when the VPN network exists unexpectedly.
     * @param reason The reason for the VPN network stop.
     */
    fun onExit(reason: String)

    /**
     * Called when the VPN network stops upon error.
     * @param errorCode is the error code.
     * @param message is the error message
     */
    fun onError(errorCode: Int, message: String)

    /**
     * Called when the VPN network detects a DNS resource is resolved
     * @param dnsRR is the DNS record
     */
    fun onDnsResolved(dnsRR: DnsRR)

    /**
     * Called by the VPN network to know if a domain is blocked or not. This is used to perform DNS-base tracker blocking
     * @param domainRR is the domain record
     */
    fun isDomainBlocked(domainRR: DomainRR): Boolean

    /**
     * Called by the VPN network to report an error parsing TLS packets.
     * The implementation of this method should just log the issue and continue
     */
    fun reportTLSParsingError(errorCode: Int) {}

    /**
     * Called by the VPN network to know if a particular IP address is blocked or not. This can be combined with the
     * [onDnsResolved] callback, to get the hostname of the [addressRR] and then decide whether that hostname should
     * be blocked or not
     * @param addressRR is the address record
     */
    fun isAddressBlocked(addressRR: AddressRR): Boolean
}

/**
 * DNS record type
 * [time] is the time the DNS resource was resolved
 * [qName] is the DNS record QName
 * [aName] is the DNS record AName
 * [resource] is the resource (IP address)
 * [ttl] is the time to live of the DNS record
 */
data class DnsRR(val time: Long, val qName: String, val aName: String, val resource: String, val ttl: Int)

/**
 * SNI record type
 * [name] is the name of the server
 * [resource] is the address of the server
 */
data class SniRR(val name: String, val resource: String)

/**
 * Domain record type
 * [name] is the name of the domain
 * [uid] is the UID of the app that's trying to access the domain
 */
data class DomainRR(val name: String, val uid: Int)

/**
 * Address record type
 * [address] is the [String] IP address
 * [uid] is the UID of the app that's trying to access the address
 */
data class AddressRR(val address: String, val uid: Int)
