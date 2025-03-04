/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.service.store

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.di.ProcessName
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

interface AutofillFeatureRepository {
    fun insertAll(newList: List<AutofillServiceException>)
    val exceptions: CopyOnWriteArrayList<String>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAutofillFeatureRepository @Inject constructor(
    private val autofillServiceDatabase: AutofillServiceDatabase,
    @IsMainProcess private val isMainProcess: Boolean,
    @ProcessName private val processName: String,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : AutofillFeatureRepository {

    private val dao = autofillServiceDatabase.exceptionsDao()

    override val exceptions = CopyOnWriteArrayList<String>()

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            Timber.i("DDGAutofillService: Init AutofillFeatureRepository from $processName")
            if (isMainProcess || processName == ":autofill") {
                loadToMemory()
            }
        }
    }

    override fun insertAll(newList: List<AutofillServiceException>) {
        dao.updateAll(newList)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        dao.getAll().map { exceptions.add(it.domain) }
    }
}
