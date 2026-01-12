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

interface DeletableDataManager {
    /**
     * Used by the SyncClient to get all the deletions from each deletable feature
     */
    fun getDeletions(): SyncDeletionRequest? = null

    /**
     * Which deletable data type this deletion is for
     */
    fun getType(): DeletableType

    /**
     * Called to notify that the deletion request was successful
     */
    fun onSuccess(response: SyncDeletionResponse) {}

    /**
     * Called to notify that the deletion request failed
     */
    fun onError(syncErrorResponse: SyncErrorResponse) {}
}
