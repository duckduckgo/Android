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
 * Remembers which app owns each connection. Once an app closes its socket the kernel can no longer name the owner
 * (uid 0, or -1 when not found), yet it still retransmits the unacknowledged ClientHello; that packet must be judged
 * as the app's, like the ones before it.
 */
internal class FlowOwners(private val capacity: Int = 512) {
    data class Flow(val protocol: Int, val saddr: String, val sport: Int, val daddr: String, val dport: Int)

    private val owners = object : LinkedHashMap<Flow, Int>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Flow, Int>) = size > capacity
    }

    @Synchronized
    fun ownerOf(flow: Flow, lookedUp: Int): Int {
        if (lookedUp > 0) {
            owners[flow] = lookedUp
            return lookedUp
        }
        return owners[flow] ?: lookedUp
    }
}
