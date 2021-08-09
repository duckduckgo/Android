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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesOnboardingObserver @Inject constructor(
    private val appCoroutineScope: CoroutineScope,
    private val userEventsRepository: UserEventsRepository,
    private val favoritesOnboardingWorkRequestBuilder: FavoritesOnboardingWorkRequestBuilder
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun siteAddedAsFavoriteOnboarding() {
        appCoroutineScope.launch {
            userEventsRepository.userEvents().map { events ->
                Timber.i("FAVORITESOBSERVER $events")
                events.filter { it.id == UserEventKey.FIRST_NON_SERP_VISITED_SITE}
            }.distinctUntilChanged().collect { filteredEvents ->
                Timber.i("FAVORITESOBSERVER filtered: $filteredEvents")
                filteredEvents.find { it.payload.isNotEmpty() }?.let {
                    Timber.i("FAVORITESOBSERVER SCHEDULED $filteredEvents")
                    favoritesOnboardingWorkRequestBuilder.scheduleWork()
                }
            }
        }
    }
}