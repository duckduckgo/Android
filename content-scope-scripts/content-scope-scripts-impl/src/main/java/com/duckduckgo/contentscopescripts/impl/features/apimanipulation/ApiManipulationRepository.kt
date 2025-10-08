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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.contentscopescripts.impl.features.apimanipulation.store.ApiManipulationStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
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
    suspend fun getJsonData(): String?
}

@ContributesBinding(AppScope::class)
class RealApiManipulationRepository @Inject constructor(
    private val apiManipulationStore: ApiManipulationStore,
    private val dispatcherProvider: DispatcherProvider,
) : ApiManipulationRepository {

    override suspend fun insertJsonData(jsonData: String): Boolean {
        return withContext(dispatcherProvider.io()) { apiManipulationStore.insertJsonData(jsonData) }
    }

    override suspend fun getJsonData(): String? {
        return withContext(dispatcherProvider.io()) { apiManipulationStore.getJsonData() }
    }
}
