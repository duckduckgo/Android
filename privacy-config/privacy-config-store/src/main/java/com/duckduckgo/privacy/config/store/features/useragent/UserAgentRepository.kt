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
import com.duckduckgo.privacy.config.store.toUserAgentException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface UserAgentRepository {
    fun updateAll(exceptions: List<UserAgentExceptionEntity>)
    val defaultExceptions: CopyOnWriteArrayList<UserAgentException>
    val omitApplicationExceptions: CopyOnWriteArrayList<UserAgentException>
    val omitVersionExceptions: CopyOnWriteArrayList<UserAgentException>
}

class RealUserAgentRepository(
    val database: PrivacyConfigDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider
) : UserAgentRepository {

    private val userAgentDao: UserAgentDao = database.userAgentDao()
    override val defaultExceptions = CopyOnWriteArrayList<UserAgentException>()
    override val omitApplicationExceptions = CopyOnWriteArrayList<UserAgentException>()
    override val omitVersionExceptions = CopyOnWriteArrayList<UserAgentException>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) { loadToMemory() }
    }

    override fun updateAll(exceptions: List<UserAgentExceptionEntity>) {
        userAgentDao.updateAll(exceptions)
        loadToMemory()
    }

    private fun loadToMemory() {
        defaultExceptions.clear()
        omitApplicationExceptions.clear()
        omitVersionExceptions.clear()
        userAgentDao.getDefaultExceptions().map { defaultExceptions.add(it.toUserAgentException()) }
        userAgentDao.getApplicationExceptions().map { omitApplicationExceptions.add(it.toUserAgentException()) }
        userAgentDao.getVersionExceptions().map { omitVersionExceptions.add(it.toUserAgentException()) }
    }
}
