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

package com.duckduckgo.autofill.store.feature

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.autofill.store.AutofillDao
import com.duckduckgo.autofill.store.AutofillDatabase
import com.duckduckgo.autofill.store.AutofillExceptionEntity
import com.duckduckgo.autofill.store.toFeatureException
import com.duckduckgo.feature.toggles.api.FeatureExceptions.FeatureException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface AutofillFeatureRepository {
    fun updateAllExceptions(exceptions: List<AutofillExceptionEntity>)
    val exceptions: CopyOnWriteArrayList<FeatureException>
}

class RealAutofillFeatureRepository(
    val database: AutofillDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : AutofillFeatureRepository {

    private val autofillDao: AutofillDao = database.autofillDao()
    override val exceptions = CopyOnWriteArrayList<FeatureException>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) { loadToMemory() }
    }

    override fun updateAllExceptions(exceptions: List<AutofillExceptionEntity>) {
        autofillDao.updateAll(exceptions)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        autofillDao.getAll().map { exceptions.add(it.toFeatureException()) }
    }
}
