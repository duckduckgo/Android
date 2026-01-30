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

package com.duckduckgo.contentscopescripts.impl.features.pagecontext

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.contentscopescripts.impl.features.pagecontext.store.PageContextStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

interface PageContextRepository {
    fun insertJsonData(jsonData: String)
    fun getJsonData(): String
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPageContextRepository @Inject constructor(
    private val pageContextStore: PageContextStore,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    @IsMainProcess private val isMainProcess: Boolean,
) : PageContextRepository {

    private var jsonData: String = EMPTY_JSON

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun insertJsonData(jsonData: String) {
        coroutineScope.launch(dispatcherProvider.io()) {
            val success = pageContextStore.insertJsonData(jsonData)
            if (success) {
                setJSONData(jsonData)
            }
        }
    }

    private fun setJSONData(newJSONData: String) {
        this.jsonData = newJSONData
    }

    override fun getJsonData(): String {
        return jsonData
    }

    private suspend fun loadToMemory() {
        jsonData = pageContextStore.getJsonData() ?: EMPTY_JSON
    }

    companion object {
        const val EMPTY_JSON = "{}"
    }
}
