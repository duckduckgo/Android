/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.internal.common

import android.content.Context
import com.duckduckgo.pir.internal.common.PirJob.RunType
import dagger.assisted.AssistedFactory

@AssistedFactory
interface PirActionsRunnerFactory {
    fun create(
        context: Context,
        pirScriptToLoad: String,
        runType: RunType,
    ): RealPirActionsRunner
}
