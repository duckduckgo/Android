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

package com.duckduckgo.sync.api.engine

interface PatchableDataManager {
    /**
     * Which deletable data type this patch is for
     */
    fun getType(): DeletableType

    /**
     * Used by the SyncClient to get patches from each patchable feature.
     * Returns a [SyncPatchRequest] if there are pending updates, or null if none.
     */
    fun getPatches(): SyncPatchRequest? = null

    /**
     * Called to notify that the patch request was successful.
     */
    fun onPatchSuccess(response: SyncPatchResponse)

    /**
     * Called to notify that the patch request failed.
     */
    fun onPatchError(syncErrorResponse: SyncErrorResponse)

    /**
     * Called when sync is disabled (user disconnects or logs out).
     * Implementations should clear any pending state.
     */
    fun onSyncDisabled()
}
