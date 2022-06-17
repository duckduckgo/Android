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

package com.duckduckgo.privacy.config.store.features.https

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.privacy.config.api.HttpsException
import com.duckduckgo.privacy.config.store.HttpsExceptionEntity
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.toHttpsException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface HttpsRepository {
    fun updateAll(exceptions: List<HttpsExceptionEntity>)
    val exceptions: CopyOnWriteArrayList<HttpsException>
}

class RealHttpsRepository(
    val database: PrivacyConfigDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider
) : HttpsRepository {

    private val httpsDao: HttpsDao = database.httpsDao()
    override val exceptions = CopyOnWriteArrayList<HttpsException>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) { loadToMemory() }
    }

    override fun updateAll(exceptions: List<HttpsExceptionEntity>) {
        httpsDao.updateAll(exceptions)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        httpsDao.getAll().map { exceptions.add(it.toHttpsException()) }
    }
}
