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

package com.duckduckgo.request.filterer.store

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.feature.toggles.api.FeatureException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface RequestFiltererRepository {
    fun updateAll(exceptions: List<RequestFiltererExceptionEntity>, settings: SettingsEntity)
    var settings: SettingsEntity
    val exceptions: List<FeatureException>
}

class RealRequestFiltererRepository(
    val database: RequestFiltererDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    isMainProcess: Boolean,
) : RequestFiltererRepository {

    private val requestFiltererDao: RequestFiltererDao = database.requestFiltererDao()

    override val exceptions = CopyOnWriteArrayList<FeatureException>()
    override var settings = SettingsEntity(windowInMs = DEFAULT_WINDOW_IN_MS)

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun updateAll(
        exceptions: List<RequestFiltererExceptionEntity>,
        settings: SettingsEntity,
    ) {
        requestFiltererDao.updateAll(exceptions, settings)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        requestFiltererDao.getAllRequestFiltererExceptions().map {
            exceptions.add(it.toFeatureException())
        }
        settings = requestFiltererDao.getSettings() ?: SettingsEntity(windowInMs = DEFAULT_WINDOW_IN_MS)
    }

    companion object {
        const val DEFAULT_WINDOW_IN_MS = 200
    }
}
