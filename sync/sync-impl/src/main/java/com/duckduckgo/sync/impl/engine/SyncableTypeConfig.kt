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

package com.duckduckgo.sync.impl.engine

import com.duckduckgo.sync.api.engine.SyncableType

enum class SyncHttpMethod {
    GET,
    PATCH,
}

enum class SyncableTypeConfig(
    val type: SyncableType,
    val supportedOperations: Set<SyncHttpMethod>,
) {
    BOOKMARKS(SyncableType.BOOKMARKS, setOf(SyncHttpMethod.GET, SyncHttpMethod.PATCH)),
    CREDENTIALS(SyncableType.CREDENTIALS, setOf(SyncHttpMethod.GET, SyncHttpMethod.PATCH)),
    SETTINGS(SyncableType.SETTINGS, setOf(SyncHttpMethod.GET, SyncHttpMethod.PATCH)),
    DUCK_AI_CHATS(SyncableType.DUCK_AI_CHATS, setOf(SyncHttpMethod.PATCH)),
    ;

    companion object {
        private val byType = entries.associateBy { it.type }

        fun forType(type: SyncableType): SyncableTypeConfig {
            return byType[type] ?: error("No config for $type")
        }
    }
}

fun SyncableType.supports(method: SyncHttpMethod): Boolean {
    return SyncableTypeConfig.forType(this).supportedOperations.contains(method)
}
