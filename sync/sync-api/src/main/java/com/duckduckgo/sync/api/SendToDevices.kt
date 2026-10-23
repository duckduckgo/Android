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
 * "Send to your devices": sends a link to another device on the same Sync account.
 * Bound in ActivityScope, as it shows UI on top of the current Activity.
 */
interface SendToDevices {
    /**
     * Shows a sheet listing the other synced devices, plus a "More share options…" entry that invokes [onMoreOptions].
     * @return false if the sheet can't be shown (feature off, Sync off, or no other device) and the caller should
     * fall back to its regular share flow.
     */
    suspend fun showShareSheet(url: String, title: String, onMoreOptions: () -> Unit): Boolean
}
