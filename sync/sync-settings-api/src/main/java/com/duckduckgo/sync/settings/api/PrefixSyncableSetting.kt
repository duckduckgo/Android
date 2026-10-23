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

package com.duckduckgo.sync.settings.api

/**
 * Optional extension for a [SyncableSetting] that also reads a family of keys sharing [keyPrefix],
 * e.g. one key per device. Its own [SyncableSetting.key] is still synced as usual.
 */
interface PrefixSyncableSetting {
    val keyPrefix: String

    /**
     * When false, the setting is ignored entirely: its key is not sent and no remote entries are delivered to it.
     */
    fun isEnabled(): Boolean

    /**
     * Called for remote entries whose key starts with [keyPrefix] and doesn't match any [SyncableSetting.key].
     * @param value the decrypted value, or null if the entry was deleted.
     */
    fun onRemoteValue(key: String, value: String?)
}
