/*
 * Copyright (c) 2025 DuckDuckGo
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

import com.duckduckgo.sync.api.engine.DeletableType
import com.duckduckgo.sync.api.engine.DeletableType.DUCK_AI_CHATS
import com.duckduckgo.sync.api.engine.PatchableDataManager
import com.duckduckgo.sync.api.engine.SyncErrorResponse
import com.duckduckgo.sync.api.engine.SyncPatchRequest
import com.duckduckgo.sync.api.engine.SyncPatchResponse

class FakePatchableDataManager(
    private val patchableType: DeletableType = DUCK_AI_CHATS,
    private val fakePatch: SyncPatchRequest? = null,
) : PatchableDataManager {
    override fun getType(): DeletableType = patchableType

    override fun getPatches(): SyncPatchRequest? = fakePatch

    override fun onPatchSuccess(response: SyncPatchResponse) {}

    override fun onPatchError(syncErrorResponse: SyncErrorResponse) {}

    override fun onSyncDisabled() {}
}
