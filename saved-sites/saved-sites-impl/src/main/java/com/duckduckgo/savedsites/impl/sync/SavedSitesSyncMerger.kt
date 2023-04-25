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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.sync.api.engine.SyncChanges
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncMerger
import com.duckduckgo.sync.api.engine.SyncablePlugin
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(scope = AppScope::class, boundType = SyncablePlugin::class)
@ContributesBinding(scope = AppScope::class, boundType = SyncMerger::class)
class SavedSitesSyncMerger @Inject constructor(val savedSitesRepository: SavedSitesRepository) : SyncMerger, SyncablePlugin {
    override fun merge(changes: SyncChanges): SyncMergeResult<Boolean> {
        return SyncMergeResult.Success(true)
    }

    override fun getChanges(since: String): SyncChanges {
        return SyncChanges.empty()
    }

    override fun syncChanges(
        changes: List<SyncChanges>,
        timestamp: String
    ) {
        changes.find { it.type == BOOKMARKS }?.let { bookmarkChanges ->
            merge(bookmarkChanges)
        }
    }
}
