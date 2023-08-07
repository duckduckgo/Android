/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.webcompat.store

import com.duckduckgo.app.global.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface WebCompatRepository {
    fun updateAll(
        webCompatEntity: WebCompatEntity,
    )
    fun getWebCompatEntity(): WebCompatEntity
}

class RealWebCompatRepository constructor(
    private val database: WebCompatDatabase,
    coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : WebCompatRepository {

    private val webCompatDao: WebCompatDao = database.webCompatDao()
    private var webCompatEntity = WebCompatEntity(json = EMPTY_JSON)

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            loadToMemory()
        }
    }

    override fun updateAll(webCompatEntity: WebCompatEntity) {
        webCompatDao.updateAll(webCompatEntity)
        loadToMemory()
    }

    override fun getWebCompatEntity(): WebCompatEntity {
        return webCompatEntity
    }

    private fun loadToMemory() {
        webCompatEntity =
            webCompatDao.get() ?: WebCompatEntity(json = EMPTY_JSON)
    }

    companion object {
        const val EMPTY_JSON = "{}"
    }
}
