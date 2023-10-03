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

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.privacy.config.api.UserAgentException
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.UserAgentExceptionEntity
import com.duckduckgo.privacy.config.store.UserAgentSitesEntity
import com.duckduckgo.privacy.config.store.UserAgentStatesEntity
import com.duckduckgo.privacy.config.store.UserAgentVersionsEntity
import com.duckduckgo.privacy.config.store.toUserAgentException
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
    val defaultExceptions: CopyOnWriteArrayList<UserAgentException>
    val omitApplicationExceptions: CopyOnWriteArrayList<UserAgentException>
    val omitVersionExceptions: CopyOnWriteArrayList<UserAgentException>
    val ddgDefaultSites: CopyOnWriteArrayList<UserAgentException>
    val ddgFixedSites: CopyOnWriteArrayList<UserAgentException>
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
    override val defaultExceptions = CopyOnWriteArrayList<UserAgentException>()
    override val omitApplicationExceptions = CopyOnWriteArrayList<UserAgentException>()
    override val omitVersionExceptions = CopyOnWriteArrayList<UserAgentException>()
    override val ddgDefaultSites = CopyOnWriteArrayList<UserAgentException>()
    override val ddgFixedSites = CopyOnWriteArrayList<UserAgentException>()
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
        userAgentDao.getDefaultExceptions().map { defaultExceptions.add(it.toUserAgentException()) }
        userAgentDao.getApplicationExceptions().map { omitApplicationExceptions.add(it.toUserAgentException()) }
        userAgentDao.getVersionExceptions().map { omitVersionExceptions.add(it.toUserAgentException()) }

        ddgDefaultSites.clear()
        ddgFixedSites.clear()
        userAgentSitesDao.getDefaultSites().map { ddgDefaultSites.add(it.toUserAgentException()) }
        userAgentSitesDao.getFixedSites().map { ddgFixedSites.add(it.toUserAgentException()) }

        val states = userAgentStatesDao.get()
        if (states != null) {
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
