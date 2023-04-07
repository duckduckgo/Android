/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.savedsites.impl.sync

import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.sync.api.SyncChanges
import com.duckduckgo.sync.api.SyncEngine
import com.duckduckgo.sync.api.SyncablePlugin

class SavedSitesDataObserver(
    val syncEngine: SyncEngine,
    val savedSitesRepository: SavedSitesRepository
) : SyncablePlugin {
    override fun getChanges(since: String): SyncChanges {
        TODO("Not yet implemented")
    }

    override fun syncChanges(
        changes: List<SyncChanges>,
        timestamp: String
    ) {
        TODO("Not yet implemented")
    }
}
