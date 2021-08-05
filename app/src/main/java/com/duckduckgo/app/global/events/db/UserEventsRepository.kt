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

package com.duckduckgo.app.global.events.db

import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import kotlinx.coroutines.withContext

interface UserEventsRepository {
    suspend fun siteVisited(url: String)
}

class AppUserEventsRepository(
    private val userEventsStore: UserEventsStore,
    private val userStageStore: UserStageStore,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val dispatcher: DispatcherProvider
) : UserEventsRepository {

    override suspend fun siteVisited(url: String) {
        if (url.isEmpty()) return
        withContext(dispatcher.io()) {
            if (userStageStore.getUserAppStage() != AppStage.DAX_ONBOARDING) return@withContext
            if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)) return@withContext

            val firstVisitedSiteEvent = userEventsStore.getUserEvent(UserEventKey.FIRST_NON_SERP_VISITED_SITE)
            if (firstVisitedSiteEvent != null) return@withContext

            userEventsStore.registerUserEvent(UserEventEntity(id = UserEventKey.FIRST_NON_SERP_VISITED_SITE, payload = url))
        }
    }
}