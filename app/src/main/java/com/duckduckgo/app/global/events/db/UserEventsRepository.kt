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

import androidx.core.net.toUri
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.domain
import com.duckduckgo.app.global.events.db.UserEventsPayloadMapper.UserEventPayload
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.favoritesOnboardingEnabled
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber

interface UserEventsRepository {
    suspend fun getUserEvent(userEventKey: UserEventKey): UserEventEntity?
    suspend fun siteVisited(tabId: String, url: String, title: String)
    suspend fun clearVisitedSite()
    suspend fun userEvents(): Flow<List<UserEventEntity>>
    suspend fun visitedSiteByDomain(query: String): Int
    suspend fun moveVisitedSiteAsFavorite(): SavedSite.Favorite?
}

class AppUserEventsRepository(
    private val userEventsStore: UserEventsStore,
    private val userStageStore: UserStageStore,
    private val favoritesRepository: FavoritesRepository,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val faviconManager: Lazy<FaviconManager>,
    private val dispatcher: DispatcherProvider,
    private val variantManager: VariantManager
) : UserEventsRepository {

    private val payloadMapper = UserEventsPayloadMapper()

    override suspend fun getUserEvent(userEventKey: UserEventKey): UserEventEntity? {
        return userEventsStore.getUserEvent(userEventKey)
    }

    override suspend fun siteVisited(tabId: String, url: String, title: String) {
        if (!variantManager.favoritesOnboardingEnabled()) return

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
            Timber.i("DebugFavicon: registerUserEvent for $title, $url")
            faviconManager.get().persistCachedFavicon(tabId, url)
        }
    }

    override suspend fun visitedSiteByDomain(query: String): Int {
        val notFound = 0
        return withContext(dispatcher.io()) {
            val firstVisitedSiteEvent = userEventsStore.getUserEvent(UserEventKey.FIRST_NON_SERP_VISITED_SITE) ?: return@withContext notFound
            val payload = payloadMapper.getPayload(firstVisitedSiteEvent)
            if (payload is UserEventPayload.SitePayload) {
                runCatching {
                    payload.url.toUri().domain()
                }.onSuccess {
                    Timber.i("DebugFavicon: VisitedSiteFavicon $it vs $query")
                    if ("%$it%" == query) {
                        Timber.i("DebugFavicon: VisitedSiteFavicon found")
                        return@withContext 1
                    }
                }
            }
            return@withContext notFound
        }
    }

    override suspend fun moveVisitedSiteAsFavorite(): SavedSite.Favorite? {
        return withContext(dispatcher.io()) {
            val userEvent = getUserEvent(UserEventKey.FIRST_NON_SERP_VISITED_SITE) ?: return@withContext null
            val payload = payloadMapper.getPayload(userEvent)
            if (payload is UserEventPayload.SitePayload) {
                userEventsStore.registerUserEvent(userEvent.copy(timestamp = 0L, payload = ""))
                return@withContext favoritesRepository.insert(title = payload.title, url = payload.url)
            }
            return@withContext null
        }
    }

    override suspend fun clearVisitedSite() {
        if (!variantManager.favoritesOnboardingEnabled()) return

        withContext(dispatcher.io()) {
            val firstVisitedSiteEvent = userEventsStore.getUserEvent(UserEventKey.FIRST_NON_SERP_VISITED_SITE) ?: return@withContext
            val payload = payloadMapper.getPayload(firstVisitedSiteEvent)
            userEventsStore.registerUserEvent(firstVisitedSiteEvent.copy(timestamp = 0L, payload = ""))
            if (payload is UserEventPayload.SitePayload) {
                faviconManager.get().deletePersistedFavicon(payload.url, forceDelete = true)
            }
        }
    }

    override suspend fun userEvents(): Flow<List<UserEventEntity>> {
        return userEventsStore.userEvents()
    }
}
