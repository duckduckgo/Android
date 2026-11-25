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
 * Use this class to launch the Sync settings.
 *
 * ```kotlin
 * globalActivityStarter.start(context, SyncActivityWithEmptyParams)
 * ```
 */
object SyncActivityWithEmptyParams : GlobalActivityStarter.ActivityParams {
    private fun readResolve(): Any = SyncActivityWithEmptyParams
}

/**
 * Use this class to launch the Sync settings and pair with the device whose code the URL carries.
 * This supports scanning a sync setup QR code with the system camera app while we're the default browser.
 * A URL that isn't a valid setup URL only opens the Sync settings.
 *
 * ```kotlin
 * globalActivityStarter.start(context, SyncActivityFromSetupUrl("https://duckduckgo.com/sync/pairing/#&code=ABC-123&deviceName=iPhone"))
 * ```
 *
 * @property url sync pairing URL from the scanned QR code; it isn't expected to be hand-crafted.
 */
data class SyncActivityFromSetupUrl(
    val url: String,
) : GlobalActivityStarter.ActivityParams

/**
 * Use this class to launch the sync screen and immediately start a Sync flow.
 * Sync checks the account when it opens: a user who doesn't sync yet sets up Sync on this device,
 * and a user who already syncs adds another device.
 *
 * ```kotlin
 * globalActivityStarter.start(context, SyncActivityStartSyncFlow(source = null))
 * ```
 *
 * @property source optional telemetry tag identifying where the flow was started from.
 * @property joinExistingAccount if the user doesn't sync yet, offers to scan a code from another device instead of creating a new account.
 */
data class SyncActivityStartSyncFlow(
    val source: String?,
    val joinExistingAccount: Boolean = false,
) : GlobalActivityStarter.ActivityParams
