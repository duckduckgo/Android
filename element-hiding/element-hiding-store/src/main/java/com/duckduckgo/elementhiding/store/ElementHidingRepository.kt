/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.elementhiding.store

import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface ElementHidingRepository {
    fun updateAll(
        elementHidingEntity: ElementHidingEntity,
    )
    var elementHidingEntity: ElementHidingEntity
}

class RealElementHidingRepository constructor(
    val database: ElementHidingDatabase,
    val coroutineScope: CoroutineScope,
    val dispatcherProvider: DispatcherProvider,
    isMainProcess: Boolean,
) : ElementHidingRepository {

    private val elementHidingDao: ElementHidingDao = database.elementHidingDao()
    override var elementHidingEntity = ElementHidingEntity(json = EMPTY_JSON)

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun updateAll(elementHidingEntity: ElementHidingEntity) {
        elementHidingDao.updateAll(elementHidingEntity)
        loadToMemory()
    }

    private fun loadToMemory() {
        elementHidingEntity =
            elementHidingDao.get() ?: ElementHidingEntity(json = EMPTY_JSON)
    }

    companion object {
        const val EMPTY_JSON = "{}"
    }
}
