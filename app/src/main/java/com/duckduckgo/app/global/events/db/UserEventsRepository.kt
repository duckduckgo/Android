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
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.events.db.UserEventsPayloadMapper.UserEventPayload
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface UserEventsRepository {
    suspend fun getUserEvent(userEventKey: UserEventKey): UserEventEntity?
    suspend fun siteVisited(tabId: String, url: String, title: String)
    suspend fun clearVisitedSite()
    suspend fun userEvents(): Flow<List<UserEventEntity>>
}

class AppUserEventsRepository(
    private val userEventsStore: UserEventsStore,
    private val userStageStore: UserStageStore,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val faviconManager: FaviconManager,
    private val dispatcher: DispatcherProvider,
) : UserEventsRepository {

    private val payloadMapper = UserEventsPayloadMapper()

    override suspend fun getUserEvent(userEventKey: UserEventKey): UserEventEntity? {
        return userEventsStore.getUserEvent(userEventKey)
    }

    override suspend fun siteVisited(tabId: String, url: String, title: String) {
        if (url.isEmpty()) return
        withContext(dispatcher.io()) {
            if (userStageStore.getUserAppStage() != AppStage.DAX_ONBOARDING) return@withContext
            if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)) return@withContext

            val firstVisitedSiteEvent = userEventsStore.getUserEvent(UserEventKey.FIRST_NON_SERP_VISITED_SITE)
            if (firstVisitedSiteEvent != null) return@withContext

            userEventsStore.registerUserEvent(
                with(UserEventEntity(id = UserEventKey.FIRST_NON_SERP_VISITED_SITE)) {
                    payloadMapper.addPayload(this, UserEventPayload.SitePayload(url, title))
                }
            )
            faviconManager.persistCachedFavicon(tabId, url)
        }
    }

    override suspend fun clearVisitedSite() {
        withContext(dispatcher.io()) {
            val firstVisitedSiteEvent = userEventsStore.getUserEvent(UserEventKey.FIRST_NON_SERP_VISITED_SITE) ?: return@withContext
            val payload = payloadMapper.getPayload(firstVisitedSiteEvent)
            userEventsStore.registerUserEvent(firstVisitedSiteEvent.copy(payload = ""))
            if (payload is UserEventPayload.SitePayload) {
                faviconManager.deletePersistedFavicon(payload.url, forceDelete = true)
            }
        }
    }

    override suspend fun userEvents(): Flow<List<UserEventEntity>> {
        return userEventsStore.userEvents()
    }
}
