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

package com.duckduckgo.eventhub.impl.pixels

import com.duckduckgo.common.utils.CurrentTimeProvider
import java.time.LocalDateTime

internal class FakeCurrentTimeProvider : CurrentTimeProvider {
    var time: Long = 0L
    override fun currentTimeMillis(): Long = time
    override fun elapsedRealtime(): Long = time
    override fun localDateTimeNow(): LocalDateTime = LocalDateTime.now()
}

internal class InMemoryEventHubRepository : EventHubRepository {
    private val states = linkedMapOf<String, PixelState>()
    override fun getPixelState(name: String): PixelState? = states[name]
    override fun getAllPixelStates(): List<PixelState> = states.values.toList()
    override fun savePixelState(state: PixelState) {
        states[state.pixelName] = state
    }
    override fun deletePixelState(name: String) {
        states.remove(name)
    }
    override fun deleteAllPixelStates() = states.clear()
}
