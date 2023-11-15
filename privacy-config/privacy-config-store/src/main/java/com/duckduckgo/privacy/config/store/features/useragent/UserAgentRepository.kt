/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.privacy.config.store.features.useragent

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.feature.toggles.api.FeatureExceptions.FeatureException
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.UserAgentExceptionEntity
import com.duckduckgo.privacy.config.store.UserAgentSitesEntity
import com.duckduckgo.privacy.config.store.UserAgentStatesEntity
import com.duckduckgo.privacy.config.store.UserAgentVersionsEntity
import com.duckduckgo.privacy.config.store.toFeatureException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface UserAgentRepository {
    fun updateAll(
        exceptions: List<UserAgentExceptionEntity>,
        sites: List<UserAgentSitesEntity>,
        states: UserAgentStatesEntity?,
        versions: List<UserAgentVersionsEntity>,
    )
    val defaultExceptions: CopyOnWriteArrayList<FeatureException>
    val omitApplicationExceptions: CopyOnWriteArrayList<FeatureException>
    val omitVersionExceptions: CopyOnWriteArrayList<FeatureException>
    val ddgDefaultSites: CopyOnWriteArrayList<FeatureException>
    val ddgFixedSites: CopyOnWriteArrayList<FeatureException>
    var defaultPolicy: String
    var closestUserAgentState: Boolean
    var ddgFixedUserAgentState: Boolean
    var closestUserAgentVersions: CopyOnWriteArrayList<String>
    var ddgFixedUserAgentVersions: CopyOnWriteArrayList<String>
}

class RealUserAgentRepository(
    val database: PrivacyConfigDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : UserAgentRepository {

    private val userAgentDao: UserAgentDao = database.userAgentDao()
    private val userAgentSitesDao: UserAgentSitesDao = database.userAgentSitesDao()
    private val userAgentStatesDao: UserAgentStatesDao = database.userAgentStatesDao()
    private val userAgentVersionsDao: UserAgentVersionsDao = database.userAgentVersionsDao()
    override val defaultExceptions = CopyOnWriteArrayList<FeatureException>()
    override val omitApplicationExceptions = CopyOnWriteArrayList<FeatureException>()
    override val omitVersionExceptions = CopyOnWriteArrayList<FeatureException>()
    override val ddgDefaultSites = CopyOnWriteArrayList<FeatureException>()
    override val ddgFixedSites = CopyOnWriteArrayList<FeatureException>()
    override var defaultPolicy: String = "ddg"
    override var closestUserAgentState: Boolean = false
    override var ddgFixedUserAgentState: Boolean = false
    override var closestUserAgentVersions = CopyOnWriteArrayList<String>()
    override var ddgFixedUserAgentVersions = CopyOnWriteArrayList<String>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) { loadToMemory() }
    }

    override fun updateAll(
        exceptions: List<UserAgentExceptionEntity>,
        sites: List<UserAgentSitesEntity>,
        states: UserAgentStatesEntity?,
        versions: List<UserAgentVersionsEntity>,
    ) {
        userAgentDao.updateAll(exceptions)
        userAgentSitesDao.updateAll(sites)
        states?.let {
            userAgentStatesDao.update(it)
        }
        userAgentVersionsDao.updateAll(versions)
        loadToMemory()
    }

    private fun loadToMemory() {
        defaultExceptions.clear()
        omitApplicationExceptions.clear()
        omitVersionExceptions.clear()
        userAgentDao.getDefaultExceptions().map { defaultExceptions.add(it.toFeatureException()) }
        userAgentDao.getApplicationExceptions().map { omitApplicationExceptions.add(it.toFeatureException()) }
        userAgentDao.getVersionExceptions().map { omitVersionExceptions.add(it.toFeatureException()) }

        ddgDefaultSites.clear()
        ddgFixedSites.clear()
        userAgentSitesDao.getDefaultSites().map { ddgDefaultSites.add(it.toFeatureException()) }
        userAgentSitesDao.getFixedSites().map { ddgFixedSites.add(it.toFeatureException()) }

        val states = userAgentStatesDao.get()
        if (states?.defaultPolicy != null && states.closestUserAgent != null && states.ddgFixedUserAgent != null) {
            defaultPolicy = states.defaultPolicy
            closestUserAgentState = states.closestUserAgent
            ddgFixedUserAgentState = states.ddgFixedUserAgent
        } else {
            defaultPolicy = "ddg"
            closestUserAgentState = false
            ddgFixedUserAgentState = false
        }

        closestUserAgentVersions.clear()
        ddgFixedUserAgentVersions.clear()
        userAgentVersionsDao.getClosestUserAgentVersions().map { closestUserAgentVersions.add(it.version) }
        userAgentVersionsDao.getDdgFixedUserAgentVerions().map { ddgFixedUserAgentVersions.add(it.version) }
    }
}
