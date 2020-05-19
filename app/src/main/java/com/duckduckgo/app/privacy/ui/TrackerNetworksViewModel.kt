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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TdsEntity
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import java.util.*

class TrackerNetworksViewModel : ViewModel() {

    data class ViewState(
        val domain: String,
        val allTrackersBlocked: Boolean,
        val trackerCount: Int,
        val trackingEventsByNetwork: SortedMap<Entity, List<TrackingEvent>>
    )

    val viewState: MutableLiveData<ViewState> = MutableLiveData()

    init {
        resetViewState()
    }

    private fun resetViewState() {
        viewState.value = ViewState(
            domain = "",
            trackerCount = 0,
            allTrackersBlocked = true,
            trackingEventsByNetwork = emptySortedTrackingEventMap()
        )
    }

    fun onSiteChanged(site: Site?) {
        if (site == null) {
            resetViewState()
            return
        }
        viewState.value = viewState.value?.copy(
            domain = site.domain ?: "",
            trackerCount = site.trackerCount,
            allTrackersBlocked = site.allTrackersBlocked,
            trackingEventsByNetwork = distinctTrackersByEntity(site.trackingEvents)
        )
    }

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
