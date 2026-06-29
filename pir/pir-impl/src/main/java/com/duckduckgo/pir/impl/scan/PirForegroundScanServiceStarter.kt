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
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.pir.impl.scheduling.PirExecutionType
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/** Starts [PirForegroundScanService] to resume an interrupted initial scan from the dashboard. */
interface PirForegroundScanServiceStarter {
    fun startResumeScan()
}

@ContributesBinding(ActivityScope::class)
class RealPirForegroundScanServiceStarter @Inject constructor(
    private val context: Context,
) : PirForegroundScanServiceStarter {
    override fun startResumeScan() {
        context.startForegroundService(
            PirForegroundScanService.intentFor(context, PirExecutionType.MANUAL_INITIAL_RESUME),
        )
    }
}
