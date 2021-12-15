/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.store.features.drm

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.privacy.config.api.DrmException
import com.duckduckgo.privacy.config.store.DrmExceptionEntity
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.toDrmException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

interface DrmRepository {
    fun updateAll(exceptions: List<DrmExceptionEntity>)
    val exceptions: CopyOnWriteArrayList<DrmException>
}

class RealDrmRepository(val database: PrivacyConfigDatabase, coroutineScope: CoroutineScope, dispatcherProvider: DispatcherProvider) :
    DrmRepository {

    private val drmDao: DrmDao = database.drmDao()
    override val exceptions = CopyOnWriteArrayList<DrmException>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            loadToMemory()
        }
    }

    override fun updateAll(exceptions: List<DrmExceptionEntity>) {
        drmDao.updateAll(exceptions)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        drmDao.getAll().map {
            exceptions.add(it.toDrmException())
        }
    }
}
