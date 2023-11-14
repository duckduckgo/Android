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

package com.duckduckgo.autofill.store.feature.email.incontext

import com.duckduckgo.common.utils.DispatcherProvider
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface EmailProtectionInContextFeatureRepository {
    fun updateAllExceptions(exceptions: List<EmailInContextExceptionEntity>)
    val exceptions: CopyOnWriteArrayList<String>
}

class RealEmailProtectionInContextFeatureRepository(
    val database: EmailProtectionInContextDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : EmailProtectionInContextFeatureRepository {

    private val dao = database.emailInContextDao()
    override val exceptions = CopyOnWriteArrayList<String>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) { loadToMemory() }
    }

    override fun updateAllExceptions(exceptions: List<EmailInContextExceptionEntity>) {
        dao.updateAll(exceptions)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        dao.getAll().map { exceptions.add(it.domain) }
    }
}
