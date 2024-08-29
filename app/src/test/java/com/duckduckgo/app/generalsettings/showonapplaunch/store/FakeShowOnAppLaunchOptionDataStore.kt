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

package com.duckduckgo.app.generalsettings.showonapplaunch.store

import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull

class FakeShowOnAppLaunchOptionDataStore : ShowOnAppLaunchOptionDataStore {

    private var currentOptionStateFlow = MutableStateFlow<ShowOnAppLaunchOption?>(null)

    override val optionFlow: Flow<ShowOnAppLaunchOption> = currentOptionStateFlow.asStateFlow().filterNotNull()

    override suspend fun setShowOnAppLaunchOption(showOnAppLaunchOption: ShowOnAppLaunchOption) {
        currentOptionStateFlow.value = showOnAppLaunchOption
    }
}
