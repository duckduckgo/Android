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

import com.duckduckgo.navigation.api.GlobalActivityStarter

/**
 * Dashboard launches that carry free scan attribution. Launches without attribution use
 * [com.duckduckgo.pir.api.PirScreens.PirDashboardWebViewScreen].
 */
sealed class PirFreemiumDashboardScreens : GlobalActivityStarter.ActivityParams {

    /** Opened from an entry point the caller already knows, such as the Settings row. */
    data class FromEntryPoint(val entryPoint: PirFreeScanEntryPoint) : PirFreemiumDashboardScreens()

    /**
     * Opened through the `pir.main` deeplink. Remote messages use it for the free scan and scan results banners,
     * so the dashboard works out which banner it was from the first scan result.
     */
    data object FromRemoteMessage : PirFreemiumDashboardScreens()
}
