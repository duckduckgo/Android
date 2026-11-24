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

package com.duckduckgo.sync.impl.ui.dashboard

import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.sync.api.SyncActivityStartSyncFlow

/**
 * [SyncActivityStartSyncFlow] with the options only Sync's own promotions use.
 *
 * @property offerDesktopApp if the user already syncs, first asks whether to sync with a desktop app they have or get one.
 */
internal data class SyncActivityStartSyncFlowInternal(
    val source: String?,
    val joinExistingAccount: Boolean = false,
    val offerDesktopApp: Boolean = false,
) : GlobalActivityStarter.ActivityParams
