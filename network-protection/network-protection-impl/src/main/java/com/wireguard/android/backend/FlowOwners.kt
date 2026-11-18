/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.wireguard.android.backend

/**
 * Remembers which app owns each connection, and the server name it was blocked for. Once an app closes its socket
 * the kernel can no longer name the owner (uid 0, or -1 when not found), yet it still retransmits the unacknowledged
 * ClientHello; that packet must be judged as the app's, like the ones before it. A retransmitted hello on a connection
 * already blocked is the same tracking attempt, so it is dropped without being judged, and counted, again.
 */
internal class FlowOwners(private val capacity: Int = 512) {
    data class Flow(val protocol: Int, val saddr: String, val sport: Int, val daddr: String, val dport: Int)

    private class Seen(var owner: Int, var blockedSni: String? = null)

    private val flows = object : LinkedHashMap<Flow, Seen>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Flow, Seen>) = size > capacity
    }

    @Synchronized
    fun ownerOf(flow: Flow, lookedUp: Int): Int {
        if (lookedUp > 0) {
            flows.getOrPut(flow) { Seen(lookedUp) }.owner = lookedUp
            return lookedUp
        }
        return flows[flow]?.owner ?: lookedUp
    }

    @Synchronized
    fun isBlocked(flow: Flow, sni: String): Boolean = flows[flow]?.blockedSni == sni

    /** Only a connection with a known owner can be blocked, so it is already remembered. */
    @Synchronized
    fun block(flow: Flow, sni: String) {
        flows[flow]?.blockedSni = sni
    }
}
