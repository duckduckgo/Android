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

package com.duckduckgo.pir.impl.scan

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Injectable wrapper around [PirForegroundScanService.isServiceRunning] so the running-state check
 * can be mocked and unit-tested by callers (the dashboard resume guard and the scheduled worker guard).
 */
interface PirForegroundScanServiceMonitor {
    fun isRunning(): Boolean
}

@ContributesBinding(AppScope::class)
class RealPirForegroundScanServiceMonitor @Inject constructor(
    private val context: Context,
) : PirForegroundScanServiceMonitor {
    override fun isRunning(): Boolean = PirForegroundScanService.isServiceRunning(context)
}
