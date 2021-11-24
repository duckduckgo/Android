/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.privacy.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TdsEntity
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Module
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject
import javax.inject.Provider

class TrackerNetworksViewModel @Inject constructor(
    private val tabRepository: TabRepository
) : ViewModel() {

    data class ViewState(
        val domain: String,
        val allTrackersBlocked: Boolean,
        val trackerCount: Int,
        val trackingEventsByNetwork: SortedMap<Entity, List<TrackingEvent>>
    )

    fun trackers(tabId: String): StateFlow<ViewState> = flow {
        tabRepository.retrieveSiteData(tabId).asFlow().collect { site ->
            emit(updatedState(site))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), resetState())

    private fun updatedState(site: Site) = ViewState(
        domain = site.domain ?: "",
        trackerCount = site.trackerCount,
        allTrackersBlocked = site.allTrackersBlocked,
        trackingEventsByNetwork = distinctTrackersByEntity(site.trackingEvents)
    )

    private fun resetState() = ViewState(
        domain = "",
        trackerCount = 0,
        allTrackersBlocked = true,
        trackingEventsByNetwork = emptySortedTrackingEventMap()
    )

    private fun distinctTrackersByEntity(trackingEvents: List<TrackingEvent>): SortedMap<Entity, List<TrackingEvent>> {
        val data = emptySortedTrackingEventMap()
        for (event: TrackingEvent in trackingEvents.distinctBy { Uri.parse(it.trackerUrl).baseHost }) {
            val network = event.entity ?: createEntity(event.trackerUrl)
            val events = (data[network] ?: emptyList()).toMutableList()
            events.add(event)
            data[network] = events
        }
        return data
    }

    private fun createEntity(trackerUrl: String): Entity {
        val name = Uri.parse(trackerUrl).baseHost ?: trackerUrl
        return TdsEntity(name, name, 0.0)
    }

    private fun emptySortedTrackingEventMap(): SortedMap<Entity, List<TrackingEvent>> {
        val comparator = compareBy<Entity> { !it.isMajor }.thenBy { it.displayName }
        return emptyMap<Entity, List<TrackingEvent>>().toSortedMap(comparator)
    }
}

@Module
@ContributesMultibinding(AppScope::class)
class TrackerNetworksViewModelFactory @Inject constructor(
    private val viewModel: Provider<TrackerNetworksViewModel>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(TrackerNetworksViewModel::class.java) -> (viewModel.get() as T)
                else -> null
            }
        }
    }
}
