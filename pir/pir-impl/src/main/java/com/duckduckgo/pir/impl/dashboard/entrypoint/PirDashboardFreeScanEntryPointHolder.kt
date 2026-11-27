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

package com.duckduckgo.pir.impl.dashboard.entrypoint

import com.duckduckgo.di.scopes.ActivityScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import javax.inject.Inject

/**
 * Gives the dashboard's web message handlers the free scan entry point, which the dashboard ViewModel resolves.
 */
interface PirDashboardFreeScanEntryPointHolder {
    var freeScanEntryPoint: Deferred<PirFreeScanEntryPoint?>
}

@SingleIn(ActivityScope::class)
@ContributesBinding(ActivityScope::class)
class RealPirDashboardFreeScanEntryPointHolder @Inject constructor() : PirDashboardFreeScanEntryPointHolder {
    override var freeScanEntryPoint: Deferred<PirFreeScanEntryPoint?> = CompletableDeferred(value = null)
}
