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

package com.duckduckgo.app.statistics.wideevents

import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.CleanupDecision
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEvent
import org.mockito.kotlin.mock

/** Repository whose cleanup entry points are scriptable; everything else is delegated to a Mockito mock. */
internal class FakeWideEventRepository(
    private val delegate: WideEventRepository = mock(),
) : WideEventRepository by delegate {
    var activeWideEventIds: suspend () -> List<Long> = { emptyList() }
    var onCleanUp: suspend (eventId: Long, decide: (WideEvent) -> CleanupDecision?) -> Unit = { _, _ -> }

    var sweeps = 0
        private set

    override suspend fun getActiveWideEventIds(): List<Long> {
        sweeps++
        return activeWideEventIds()
    }

    override suspend fun cleanUpWideEvent(
        eventId: Long,
        decide: (WideEvent) -> CleanupDecision?,
    ) = onCleanUp(eventId, decide)
}
