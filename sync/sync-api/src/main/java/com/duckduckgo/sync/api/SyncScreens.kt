/*
 * Copyright (c) 2023 DuckDuckGo
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

import com.duckduckgo.navigation.api.GlobalActivityStarter

/**
 * Use this class to launch the sync screen without parameters
 * ```kotlin
 * globalActivityStarter.start(context, SyncActivityWithEmptyParams)
 * ```
 */
object SyncActivityWithEmptyParams : GlobalActivityStarter.ActivityParams

/**
 * Use this class to launch the sync screen with a URL-based sync pairing code
 * It is not expected that the URL would be hand-crafted
 * This is to support the flow when a URL-based sync setup QR code is scanned using the normal camera app and we're the default browser
 *
 * ```kotlin
 * globalActivityStarter.start(context, SyncActivityFromSetupUrl("https://duckduckgo.com/sync/pairing/#&code=ABC-123&deviceName=iPhone"))
 * ```
 */
data class SyncActivityFromSetupUrl(val url: String) : GlobalActivityStarter.ActivityParams
