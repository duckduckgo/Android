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

package com.duckduckgo.sync.impl.ui

import com.duckduckgo.sync.impl.pixels.SyncPixelParameters

enum class SyncEntryPoint {
    SYNC_NEW_ACCOUNT,
    ADD_DEVICE,
    RECOVER_SYNCED_DATA,
}

fun SyncEntryPoint.toAutoRestorePixelSource(): String = when (this) {
    SyncEntryPoint.ADD_DEVICE -> SyncPixelParameters.AUTO_RESTORE_SOURCE_PAIRING
    SyncEntryPoint.SYNC_NEW_ACCOUNT -> SyncPixelParameters.AUTO_RESTORE_SOURCE_BACKUP
    SyncEntryPoint.RECOVER_SYNCED_DATA -> SyncPixelParameters.AUTO_RESTORE_SOURCE_RECOVER
}
