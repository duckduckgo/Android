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

package com.duckduckgo.autoconsent.impl.remoteconfig

import com.duckduckgo.autoconsent.store.AutoconsentDatabase
import com.duckduckgo.autoconsent.store.AutoconsentExceptionEntity
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.feature.toggles.api.FeatureExceptions.FeatureException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface AutoconsentExceptionsRepository {
    fun insertAllExceptions(exceptions: List<FeatureException>)
    val exceptions: CopyOnWriteArrayList<FeatureException>
}

class RealAutoconsentExceptionsRepository(
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    val database: AutoconsentDatabase,
    isMainProcess: Boolean,
) : AutoconsentExceptionsRepository {

    private val dao = database.autoconsentDao()
    override val exceptions = CopyOnWriteArrayList<FeatureException>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun insertAllExceptions(exceptions: List<FeatureException>) {
        dao.updateAllExceptions(exceptions.map { AutoconsentExceptionEntity(domain = it.domain, reason = it.reason ?: "") })
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        val exceptionsEntityList = dao.getExceptions()
        exceptions.addAll(exceptionsEntityList.map { FeatureException(domain = it.domain, reason = it.reason) })
    }
}
