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

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.cookies.api.TrackingCookie1pException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

interface CookiesRepository {
    fun updateAll(exceptions: List<TrackingCookie1pExceptionEntity>, firstPartyTrackerCookiePolicy: FirstPartyTrackerCookiePolicyEntity)
    var firstPartyCookieTrackerPolicy: FirstPartyTrackerCookiePolicyEntity
    val exceptions: List<TrackingCookie1pException>
}

class RealCookieRepository constructor(
    database: CookiesDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider
) : CookiesRepository {

    private val cookiesDao: CookiesDao = database.cookiesDao()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            loadToMemory()
        }
    }

    override val exceptions = CopyOnWriteArrayList<TrackingCookie1pException>()
    override var firstPartyCookieTrackerPolicy = FirstPartyTrackerCookiePolicyEntity(threshold = DEFAULT_THRESHOLD, maxAge = DEFAULT_MAX_AGE)

    override fun updateAll(
        exceptions: List<TrackingCookie1pExceptionEntity>,
        firstPartyTrackerCookiePolicy: FirstPartyTrackerCookiePolicyEntity
    ) {
        cookiesDao.updateAll(exceptions, firstPartyTrackerCookiePolicy)
        loadToMemory()
    }

    private fun loadToMemory() {
        exceptions.clear()
        cookiesDao.getAllTrackingCookie1pExceptions().map {
            exceptions.add(it.toTrackingCookie1p())
        }
        firstPartyCookieTrackerPolicy =
            cookiesDao.getFirstPartyCookieTrackersPolicy()
                ?: FirstPartyTrackerCookiePolicyEntity(threshold = DEFAULT_THRESHOLD, maxAge = DEFAULT_MAX_AGE)
    }

    companion object {
        const val DEFAULT_THRESHOLD = 1
        const val DEFAULT_MAX_AGE = 1
    }
}
