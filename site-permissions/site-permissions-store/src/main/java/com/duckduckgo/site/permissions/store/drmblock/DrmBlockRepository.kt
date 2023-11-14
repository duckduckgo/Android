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

package com.duckduckgo.site.permissions.store.drmblock

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.feature.toggles.api.FeatureExceptions.FeatureException
import com.duckduckgo.site.permissions.store.SitePermissionsDatabase
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface DrmBlockRepository {
    fun updateAll(exceptions: List<DrmBlockExceptionEntity>)
    val exceptions: CopyOnWriteArrayList<FeatureException>
}

class RealDrmBlockRepository(
    val database: SitePermissionsDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : DrmBlockRepository {

    private val drmBlockDao: DrmBlockDao = database.drmBlockDao()
    override val exceptions = CopyOnWriteArrayList<FeatureException>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            loadToMemory()
        }
    }

    override fun updateAll(exceptions: List<DrmBlockExceptionEntity>) {
        drmBlockDao.updateAll(exceptions)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        drmBlockDao.getAll().map {
            exceptions.add(it.toFeatureException())
        }
    }
}
