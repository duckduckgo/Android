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

/**
 * Decides whether to suggest turning on Sync on a phone that was set up by a device transfer.
 *
 * The suggestion is due when the install came from a device transfer that has finished, neither the previous phone
 * nor this one has Sync, and the user has not dismissed it. Accepting the suggestion should open
 * [SyncActivityStartSetupFlow]; that does not dismiss it, so it stays due until Sync is on.
 */
interface SyncAfterDeviceTransferPrompt {
    suspend fun shouldShow(): Boolean

    /** Stops suggesting Sync on this install for good. */
    suspend fun dismiss()
}
