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

package com.duckduckgo.cookies.store

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.cookies.store.thirdpartycookienames.ThirdPartyCookieNamesDao
import com.duckduckgo.feature.toggles.api.FeatureException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

interface CookiesRepository {
    fun updateAll(
        exceptions: List<CookieExceptionEntity>,
        firstPartyTrackerCookiePolicy: FirstPartyCookiePolicyEntity,
        cookieNameEntities: List<CookieNamesEntity>,
    )
    var firstPartyCookiePolicy: FirstPartyCookiePolicyEntity
    val exceptions: List<FeatureException>
    val cookieNames: CopyOnWriteArrayList<String>
}

class RealCookieRepository constructor(
    val database: CookiesDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    isMainProcess: Boolean,
) : CookiesRepository {

    private val cookiesDao: CookiesDao = database.cookiesDao()
    private val cookieNamesDao: ThirdPartyCookieNamesDao = database.cookieNamesDao()

    override val exceptions = CopyOnWriteArrayList<FeatureException>()
    override var firstPartyCookiePolicy = FirstPartyCookiePolicyEntity(threshold = DEFAULT_THRESHOLD, maxAge = DEFAULT_MAX_AGE)
    override val cookieNames = CopyOnWriteArrayList<String>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun updateAll(
        exceptions: List<CookieExceptionEntity>,
        firstPartyTrackerCookiePolicy: FirstPartyCookiePolicyEntity,
        cookieNameEntities: List<CookieNamesEntity>,
    ) {
        cookiesDao.updateAll(exceptions, firstPartyTrackerCookiePolicy)
        cookieNamesDao.updateAllCookieNames(cookieNameEntities)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        cookiesDao.getAllCookieExceptions().map {
            exceptions.add(it.toFeatureException())
        }

        firstPartyCookiePolicy =
            cookiesDao.getFirstPartyCookiePolicy()
                ?: FirstPartyCookiePolicyEntity(threshold = DEFAULT_THRESHOLD, maxAge = DEFAULT_MAX_AGE)

        cookieNames.clear()
        val cookieNamesEntityList = cookieNamesDao.getCookieNames()
        cookieNames.addAll(cookieNamesEntityList.map { it.name })
    }

    companion object {
        const val DEFAULT_THRESHOLD = 86400
        const val DEFAULT_MAX_AGE = 86400
    }
}
