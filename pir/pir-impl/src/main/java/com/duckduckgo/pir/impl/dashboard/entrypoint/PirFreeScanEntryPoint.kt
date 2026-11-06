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

/**
 * Where a free scan user opened the dashboard from, so a subscription bought from it can be attributed to that entry point.
 */
enum class PirFreeScanEntryPoint(val token: String) {
    FREE_SCAN_BANNER("freescanbanner"),
    VIEW_RESULTS_BANNER("viewresultsbanner"),
    APP_SETTINGS_PRE_SCAN("appsettingsprescan"),
    APP_SETTINGS_POST_SCAN("appsettingspostscan"),
    SCAN_COMPLETE_NOTIFICATION("scancompletenotification"),
}

/**
 * Returns [baseOrigin] with the [entryPoint] token appended. An unknown entry point keeps [baseOrigin].
 */
fun freeScanOrigin(
    baseOrigin: String,
    entryPoint: PirFreeScanEntryPoint?,
): String = if (entryPoint == null) baseOrigin else "${baseOrigin}__${entryPoint.token}"
