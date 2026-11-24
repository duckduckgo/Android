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

import logcat.logcat

/**
 * Remembers which app owns each connection, and the server name it was blocked for. Once an app closes its socket
 * the kernel can no longer name the owner (uid 0, or -1 when not found), yet it still retransmits the unacknowledged
 * ClientHello; that packet must be judged as the app's, like the ones before it. A retransmitted hello on a connection
 * already blocked is the same tracking attempt, so it is dropped without being judged, and counted, again.
 *
 * Blocked connections are remembered apart from owners, so the allowed traffic that fills the owners can't make a
 * blocked connection's retransmission be judged again.
 */
internal class FlowOwners(private val capacity: Int = 512) {
    data class Flow(val protocol: Int, val saddr: String, val sport: Int, val daddr: String, val dport: Int) {
        override fun toString() = "$saddr:$sport > $daddr:$dport"
    }

    private val owners = lru<Int>()
    private val blocked = lru<String>()

    /**
     * Whether the hello for [sni] on [flow] may go out. [lookup] asks the kernel for the connection's owner; [judge]
     * decides for an owner, and runs once per connection that it blocks.
     */
    fun allow(flow: Flow, sni: String, lookup: () -> Int, judge: (owner: Int) -> Boolean): Boolean {
        if (synchronized(this) { blocked[flow] == sni }) {
            logcat { "$sni ($flow) already blocked on this connection" }
            return false
        }
        val lookedUp = lookup()
        val owner = ownerOf(flow, lookedUp)
        if (owner != lookedUp) {
            logcat { "Owner of $sni ($flow) is gone (uid $lookedUp), using uid $owner" }
        }
        return judge(owner).also { allowed ->
            if (!allowed) synchronized(this) { blocked[flow] = sni }
        }
    }

    @Synchronized
    private fun ownerOf(flow: Flow, lookedUp: Int): Int {
        if (lookedUp > 0) {
            owners[flow] = lookedUp
            return lookedUp
        }
        return owners[flow] ?: lookedUp
    }

    private fun <V> lru() = object : LinkedHashMap<Flow, V>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Flow, V>) = size > capacity
    }
}
