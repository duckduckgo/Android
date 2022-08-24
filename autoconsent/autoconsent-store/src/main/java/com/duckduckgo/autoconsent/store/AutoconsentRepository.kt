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

package com.duckduckgo.autoconsent.store

import com.duckduckgo.app.global.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

interface AutoconsentRepository {
    fun updateAll(exceptions: List<AutoconsentExceptionEntity>, disabledCmps: List<DisabledCmpsEntity>)
    val exceptions: List<AutoconsentExceptionEntity>
    val disabledCmps: List<DisabledCmpsEntity>
}

class RealAutoconsentRepository constructor(
    val database: AutoconsentDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider
) : AutoconsentRepository {

    private val autoconsentDao: AutoconsentDao = database.autoconsentDao()

    override val exceptions = CopyOnWriteArrayList<AutoconsentExceptionEntity>()
    override val disabledCmps = CopyOnWriteArrayList<DisabledCmpsEntity>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            loadToMemory()
        }
    }

    override fun updateAll(exceptions: List<AutoconsentExceptionEntity>, disabledCmps: List<DisabledCmpsEntity>) {
        autoconsentDao.updateAll(exceptions, disabledCmps)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        exceptions.addAll(autoconsentDao.getExceptions())

        disabledCmps.clear()
        disabledCmps.addAll(autoconsentDao.getDisabledCmps())
    }
}
