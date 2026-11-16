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

package com.duckduckgo.site.permissions.api

/**
 * Remembered answers to Duck.ai client tool permission prompts, keyed by the Duck.ai origin
 * (such as `https://duck.ai`) and the tool name.
 *
 * These choices persist across restarts, are not listed in the Site Permissions screen and are
 * not cleared by the Fire Button. Only [clear] and [clearAll] remove them.
 */
interface ClientToolPermissionStore {
    /** The remembered choice, or null when the user has not chosen Always or Never for this tool. */
    suspend fun get(
        origin: String,
        toolName: String,
    ): ClientToolPermission?

    suspend fun set(
        origin: String,
        toolName: String,
        permission: ClientToolPermission,
    )

    /** Forgets the choice for [toolName] on every origin. */
    suspend fun clear(toolName: String)

    suspend fun clearAll()
}

enum class ClientToolPermission {
    ALWAYS_ALLOW,
    NEVER_ALLOW,
}
