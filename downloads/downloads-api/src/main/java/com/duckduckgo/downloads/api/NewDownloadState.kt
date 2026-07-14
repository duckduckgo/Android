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

package com.duckduckgo.downloads.api

import kotlinx.coroutines.flow.Flow

/**
 * Tracks whether there is a completed download that the user has not yet seen, so the browser menu
 * can surface a "new download" indicator until the user opens the downloads screen.
 *
 * The state is owned entirely by the downloads feature (persisted in downloads-impl), so no consumer
 * has to reach back into :app to read or mutate it.
 */
interface NewDownloadState {
    /**
     * Reactive flow indicating whether there is a new download that has not been viewed by the user yet.
     */
    val hasNewDownloadFlow: Flow<Boolean>

    /**
     * Indicates whether there is a new download that has not been viewed by the user yet.
     */
    fun hasNewDownload(): Boolean

    /**
     * Indicates that a new download has completed, and the user should be notified about it until they view the downloads screen.
     */
    fun onDownloadComplete()
}
