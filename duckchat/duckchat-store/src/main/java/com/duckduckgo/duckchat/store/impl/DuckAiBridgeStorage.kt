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

package com.duckduckgo.duckchat.store.impl

import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatsDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeFileMetaDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingsDao
import dagger.Lazy
import java.io.File

/**
 * Groups the per-mode native Duck.ai storage backends (the three DAOs plus the chat-attachment files
 * directory) so a single mode-resolved bundle can be injected instead of four separately-qualified
 * dependencies. One instance is bound per [com.duckduckgo.browsermode.api.BrowserMode]; resolve the
 * right one via `BrowserModeDataProvider<DuckAiBridgeStorage>`.
 *
 * Migration state is intentionally NOT part of this facade — migration is a one-time, app-level event
 * (see [DuckAiMigrationPrefs]) and is shared across modes.
 */
interface DuckAiBridgeStorage {
    val settings: DuckAiBridgeSettingsDao
    val chats: DuckAiBridgeChatsDao
    val fileMeta: DuckAiBridgeFileMetaDao
    val filesDir: File
}

internal class RealDuckAiBridgeStorage(
    override val settings: DuckAiBridgeSettingsDao,
    override val chats: DuckAiBridgeChatsDao,
    override val fileMeta: DuckAiBridgeFileMetaDao,
    private val filesDirLazy: Lazy<File>,
) : DuckAiBridgeStorage {
    // Deferred so the context.filesDir access isn't done at construction time (kept off whatever
    // thread first injects the per-mode storage)
    override val filesDir: File get() = filesDirLazy.get()
}
