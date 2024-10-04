/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl.store

import android.content.Context
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope

interface AutoconsentSettingsRepository : AutoconsentSettingsDataStore {
    companion object {
        fun create(
            context: Context,
            autoconsentFeature: AutoconsentFeature,
            appCoroutineScope: CoroutineScope,
            dispatcherProvider: DispatcherProvider,
        ): AutoconsentSettingsRepository {
            val store = RealAutoconsentSettingsDataStore(context, autoconsentFeature, appCoroutineScope, dispatcherProvider)
            return RealAutoconsentSettingsRepository(store)
        }
    }
}

internal class RealAutoconsentSettingsRepository constructor(
    private val autoconsentSettingsDataStore: AutoconsentSettingsDataStore,
) : AutoconsentSettingsRepository, AutoconsentSettingsDataStore by autoconsentSettingsDataStore
