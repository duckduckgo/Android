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

package com.duckduckgo.contentscopescripts.impl.features.apimanipulation

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.contentscopescripts.impl.features.apimanipulation.store.ApiManipulationStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

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

interface ApiManipulationRepository {
    suspend fun insertJsonData(jsonData: String): Boolean
    fun getJsonData(): String
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealApiManipulationRepository @Inject constructor(
    private val apiManipulationStore: ApiManipulationStore,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    @IsMainProcess private val isMainProcess: Boolean,
) : ApiManipulationRepository {

    private var jsonData: String = EMPTY_JSON

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override suspend fun insertJsonData(jsonData: String): Boolean {
        val success = withContext(dispatcherProvider.io()) { apiManipulationStore.insertJsonData(jsonData) }
        if (success) {
            this.jsonData = jsonData
        }
        return success
    }

    override fun getJsonData(): String {
        return jsonData
    }

    private suspend fun loadToMemory() {
        jsonData = apiManipulationStore.getJsonData() ?: EMPTY_JSON
    }

    companion object {
        const val EMPTY_JSON = "{}"
    }
}
