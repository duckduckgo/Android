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
import com.duckduckgo.privacy.config.api.GpcException
import com.duckduckgo.privacy.config.store.GpcExceptionEntity
import com.duckduckgo.privacy.config.store.HttpsExceptionEntity
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.toGpcException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface HttpsRepository {
    fun updateAll(exceptions: List<HttpsExceptionEntity>)
    val exceptions: ArrayList<GpcException>
}

class RealGpcRepository(private val gpcDataStore: GpcDataStore, val database: PrivacyConfigDatabase, coroutineScope: CoroutineScope, dispatcherProvider: DispatcherProvider) :
    GpcRepository {

    private val gpcDao: GpcDao = database.gpcDao()
    override val exceptions = ArrayList<GpcException>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            loadToMemory()
        }
    }

    override fun updateAll(exceptions: List<GpcExceptionEntity>) {
        gpcDao.updateAll(exceptions)
        loadToMemory()
    }

    override fun enableGpc() {
        gpcDataStore.gpcEnabled = true
    }

    override fun disableGpc() {
        gpcDataStore.gpcEnabled = false
    }

    override fun isGpcEnabled(): Boolean = gpcDataStore.gpcEnabled

    private fun loadToMemory() {
        exceptions.clear()
        gpcDao.getAll().map {
            exceptions.add(it.toGpcException())
        }
    }
}
