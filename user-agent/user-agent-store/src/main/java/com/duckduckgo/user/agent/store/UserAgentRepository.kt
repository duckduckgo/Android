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

package com.duckduckgo.user.agent.store

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.feature.toggles.api.FeatureException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

interface UserAgentRepository {
    fun updateAll(exceptions: List<UserAgentExceptionEntity>)
    val exceptions: CopyOnWriteArrayList<FeatureException>
}

class RealUserAgentRepository(
    val database: UserAgentDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    isMainProcess: Boolean,
) : UserAgentRepository {

    private val userAgentExceptionsDao: UserAgentExceptionsDao = database.userAgentExceptionsDao()
    override val exceptions = CopyOnWriteArrayList<FeatureException>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun updateAll(exceptions: List<UserAgentExceptionEntity>) {
        userAgentExceptionsDao.updateAll(exceptions)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        userAgentExceptionsDao.getAll().map { exceptions.add(it.toFeatureException()) }
    }
}
