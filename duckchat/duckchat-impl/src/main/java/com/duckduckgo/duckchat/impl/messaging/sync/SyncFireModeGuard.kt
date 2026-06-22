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
 * Whether Duck.ai chat activity in this [BrowserMode] may enter the sync pipeline. Chats in a non-syncable mode
 * (Fire) must never sync — they stay on-device.
 */
internal val BrowserMode.isSyncable: Boolean
    get() = when (this) {
        BrowserMode.FIRE -> false
        BrowserMode.REGULAR -> true
    }
