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

package com.duckduckgo.sync.api

interface SyncAutoRestore {

    /**
     * Whether Sync auto-restore can be offered to the user.
     *
     * Returns true only when ALL of the following are met:
     * - The auto-restore feature flag is enabled
     * - An auto-restore token exists
     */
    suspend fun canRestore(): Boolean

    /**
     * Kicks off a headless sync account restore using the
     * stored auto-restore token.
     *
     * This is fire-and-forget: the restore
     * is best-effort and offers no user-facing results.
     */
    fun restoreSyncAccount()
}
