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

package com.duckduckgo.privacy.config.store.features.gpc

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.privacy.config.api.GpcException
import com.duckduckgo.privacy.config.api.GpcHeaderEnabledSite
import com.duckduckgo.privacy.config.store.GpcContentScopeConfigEntity
import com.duckduckgo.privacy.config.store.GpcExceptionEntity
import com.duckduckgo.privacy.config.store.GpcHeaderEnabledSiteEntity
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.toGpcException
import com.duckduckgo.privacy.config.store.toGpcHeaderEnabledSite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

interface GpcRepository {
    fun updateAll(
        exceptions: List<GpcExceptionEntity>,
        headerEnabledSites: List<GpcHeaderEnabledSiteEntity>,
        gpcContentScopeConfig: GpcContentScopeConfigEntity,
    )

    fun enableGpc()
    fun disableGpc()
    fun isGpcEnabled(): Boolean
    val exceptions: CopyOnWriteArrayList<GpcException>
    val headerEnabledSites: CopyOnWriteArrayList<GpcHeaderEnabledSite>
    val gpcContentScopeConfig: String
}

class RealGpcRepository(
    private val gpcDataStore: GpcDataStore,
    val database: PrivacyConfigDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    isMainProcess: Boolean,
) : GpcRepository {

    private val gpcExceptionsDao: GpcExceptionsDao = database.gpcExceptionsDao()
    private val gpcHeadersDao: GpcHeadersDao = database.gpcHeadersDao()
    private val gpcContentScopeConfigDao: GpcContentScopeConfigDao = database.gpcContentScopeConfigDao()
    override val exceptions = CopyOnWriteArrayList<GpcException>()
    override val headerEnabledSites = CopyOnWriteArrayList<GpcHeaderEnabledSite>()
    override var gpcContentScopeConfig: String = emptyJson

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun updateAll(
        exceptions: List<GpcExceptionEntity>,
        headerEnabledSites: List<GpcHeaderEnabledSiteEntity>,
        gpcContentScopeConfig: GpcContentScopeConfigEntity,
    ) {
        gpcExceptionsDao.updateAll(exceptions)
        gpcHeadersDao.updateAll(headerEnabledSites)
        gpcContentScopeConfigDao.insert(gpcContentScopeConfig)
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
        headerEnabledSites.clear()
        gpcExceptionsDao.getAll().map { exceptions.add(it.toGpcException()) }
        gpcHeadersDao.getAll().map { headerEnabledSites.add(it.toGpcHeaderEnabledSite()) }
        gpcContentScopeConfigDao.getConfig()?.let { entity ->
            gpcContentScopeConfig = entity.config
        }
    }

    companion object {
        const val emptyJson = "{}"
    }
}
