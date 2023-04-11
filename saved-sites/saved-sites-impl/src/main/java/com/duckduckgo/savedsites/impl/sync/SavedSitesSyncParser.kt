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
import com.duckduckgo.sync.api.SyncChanges
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.SyncParser
import com.duckduckgo.sync.api.SyncableType.BOOKMARKS
import com.squareup.anvil.annotations.ContributesBinding

@ContributesBinding(AppScope::class)
class SavedSitesSyncParser(
    val savedSitesRepository: SavedSitesRepository,
    val syncCrypto: SyncCrypto
) : SyncParser {
    override fun parseChanges(since: String): SyncChanges {
        return SyncChanges(BOOKMARKS, "")
    }
}
