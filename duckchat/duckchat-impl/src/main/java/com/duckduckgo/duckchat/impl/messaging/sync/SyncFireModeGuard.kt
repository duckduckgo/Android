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

package com.duckduckgo.duckchat.impl.messaging.sync

import com.duckduckgo.browsermode.api.BrowserMode

/**
 * Fire-mode Duck.ai chats must never sync. Chat content is synced by the front-end JS (it obtains the account
 * master key via [EncryptWithSyncMasterKeyHandler] and pushes to the backend itself), so storage isolation alone
 * doesn't stop it — every FE-facing sync handler must refuse when sync isn't available for its mode.
 *
 * The Duck.ai sync handlers are activity-scoped and inject the host activity's [BrowserMode] (frozen for the
 * activity's lifetime), checking availability here before doing any sync work. Exhaustive `when` so a new
 * [BrowserMode] is a compile error here.
 */
internal fun BrowserMode.isSyncAvailable(): Boolean = when (this) {
    BrowserMode.FIRE -> false
    BrowserMode.REGULAR -> true
}
