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

package com.duckduckgo.firemode.api

/**
 * Which browsing mode the user is currently in.
 *
 * [REGULAR] is the default persistent browsing experience. [FIRE] is the ephemeral
 * Fire-mode experience whose data is isolated and burned on demand.
 */
enum class BrowserMode {
    REGULAR,
    FIRE,
}
