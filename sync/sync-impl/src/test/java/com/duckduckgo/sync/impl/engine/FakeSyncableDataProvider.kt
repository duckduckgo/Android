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

package com.duckduckgo.sync.impl.engine

import com.duckduckgo.sync.api.engine.SyncChangesRequest
import com.duckduckgo.sync.api.engine.SyncableDataProvider
import com.duckduckgo.sync.api.engine.SyncableType
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS

class FakeSyncableDataProvider(
    private val syncableType: SyncableType = BOOKMARKS,
    private val fakeChanges: SyncChangesRequest,
) : SyncableDataProvider {
    override fun getType(): SyncableType = syncableType

    override fun getChanges(): SyncChangesRequest {
        return fakeChanges
    }
}
