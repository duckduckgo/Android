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

package com.duckduckgo.privacy.config.store.features.autofill

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.privacy.config.api.AutofillException
import com.duckduckgo.privacy.config.store.AutofillExceptionEntity
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.toAutofillException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface AutofillRepository {
    fun updateAll(exceptions: List<AutofillExceptionEntity>)
    val exceptions: CopyOnWriteArrayList<AutofillException>
}

class RealAutofillRepository(
    val database: PrivacyConfigDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider
) : AutofillRepository {

    private val autofillDao: AutofillDao = database.autofillDao()
    override val exceptions = CopyOnWriteArrayList<AutofillException>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) { loadToMemory() }
    }

    override fun updateAll(exceptions: List<AutofillExceptionEntity>) {
        autofillDao.updateAll(exceptions)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        autofillDao.getAll().map { exceptions.add(it.toAutofillException()) }
    }
}
