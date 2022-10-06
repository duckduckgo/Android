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

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.baseHost
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TdsEntity
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.di.scopes.ActivityScope
import java.util.*
import javax.inject.Inject
import kotlin.Comparator
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.internal.publicsuffix.PublicSuffixDatabase

@ContributesViewModel(ActivityScope::class)
class TrackerNetworksViewModel @Inject constructor(
    private val tabRepository: TabRepository
) : ViewModel(), TrackerNetworksListener {

    private val publicSuffixDatabase = PublicSuffixDatabase()

    sealed class ViewState(
        open val allTrackersBlocked: Boolean,
        open val domain: String,
        open val count: Int,
        open val eventsByNetwork: SortedMap<TrackerNetworksSection, SortedMap<Entity, List<TrackingEvent>>>
    ) {
        data class TrackersViewState(
            override val allTrackersBlocked: Boolean,
            override val eventsByNetwork: SortedMap<TrackerNetworksSection, SortedMap<Entity, List<TrackingEvent>>>,
            override val domain: String,
            override val count: Int
        ) : ViewState(allTrackersBlocked, domain, count, eventsByNetwork)

        data class DomainsViewState(
            override val allTrackersBlocked: Boolean,
            override val eventsByNetwork: SortedMap<TrackerNetworksSection, SortedMap<Entity, List<TrackingEvent>>>,
            override val domain: String,
            override val count: Int
        ) : ViewState(allTrackersBlocked, domain, count, eventsByNetwork)
    }

    sealed class Command {
        data class OpenLink(val url: String) : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    fun trackers(tabId: String, domainsLoaded: Boolean): StateFlow<ViewState> = flow {
        tabRepository.retrieveSiteData(tabId).asFlow().collect { site ->
            emit(updatedState(site, domainsLoaded))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), resetState(domainsLoaded))

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    override fun onClicked(url: String) {
        viewModelScope.launch { command.send(Command.OpenLink(url)) }
    }

    private fun updatedState(site: Site, domainsLoaded: Boolean): ViewState {
        return if (domainsLoaded) {
            ViewState.DomainsViewState(
                domain = site.domain ?: "",
                count = site.otherDomainsLoadedCount + site.specialDomainsLoadedCount,
                allTrackersBlocked = site.allTrackersBlocked,
                eventsByNetwork = distinctByEntity(site.trackingEvents, site.baseHost) { it.status != TrackerStatus.BLOCKED }
            )
        } else {
            ViewState.TrackersViewState(
                domain = site.domain ?: "",
                count = site.trackerCount,
                allTrackersBlocked = site.allTrackersBlocked,
                eventsByNetwork = distinctByEntity(site.trackingEvents, site.baseHost) { it.status == TrackerStatus.BLOCKED }
            )
        }
    }

    private fun resetState(domainsLoaded: Boolean): ViewState {
        return if (domainsLoaded) {
            ViewState.DomainsViewState(
                domain = "",
                count = 0,
                allTrackersBlocked = true,
                eventsByNetwork = emptyMap<TrackerNetworksSection, SortedMap<Entity, List<TrackingEvent>>>().toSortedMap(sectionsComparator())
            )
        } else {
            ViewState.TrackersViewState(
                domain = "",
                count = 0,
                allTrackersBlocked = true,
                eventsByNetwork = emptyMap<TrackerNetworksSection, SortedMap<Entity, List<TrackingEvent>>>().toSortedMap(sectionsComparator())
            )
        }
    }

    private fun distinctByEntity(
        trackingEvents: List<TrackingEvent>,
        domain: String? = null,
        filterPredicate: (TrackingEvent) -> Boolean
    ): SortedMap<TrackerNetworksSection, SortedMap<Entity, List<TrackingEvent>>> {
        val result = emptyMap<TrackerNetworksSection, SortedMap<Entity, List<TrackingEvent>>>().toSortedMap(sectionsComparator())
        val filteredEvents = trackingEvents.asSequence()
            .filter { filterPredicate(it) }
            .distinctBy {
                if (it.entity != null) {
                    "${(it.trackerUrl.toUri().baseHost)}-${it.status}"
                } else {
                    "${publicSuffixDatabase.getEffectiveTldPlusOne(it.trackerUrl.toUri().baseHost ?: "")}-${it.status}"
                }
            }
        for (event: TrackingEvent in filteredEvents) {
            val sectionInfo = sectionInfo(event.status, domain) ?: continue
            val data = result[sectionInfo] ?: emptySortedTrackingEventMap(trackersComparator())
            result[sectionInfo] = data
            val network = event.entity ?: createEntity(event.trackerUrl)
            val events = (data[network] ?: emptyList()).toMutableList()
            events.add(event)
            data[network] = events
        }
        return result
    }

    private fun createEntity(trackerUrl: String): Entity {
        val name = publicSuffixDatabase.getEffectiveTldPlusOne(trackerUrl.toUri().baseHost ?: trackerUrl) ?: trackerUrl
        return TdsEntity(name, name, 0.0)
    }

    private fun emptySortedTrackingEventMap(comparator: Comparator<Entity>): SortedMap<Entity, List<TrackingEvent>> =
        emptyMap<Entity, List<TrackingEvent>>().toSortedMap(comparator)

    private fun trackersComparator(): Comparator<Entity> = compareBy<Entity> { !it.isMajor }.thenBy { it.displayName }

    private fun sectionsComparator(): Comparator<TrackerNetworksSection> = compareBy { it.trackerStatus }

    private fun sectionInfo(status: TrackerStatus, domain: String?): TrackerNetworksSection? {
        if (status == TrackerStatus.BLOCKED) {
            return TrackerNetworksSection(
                trackerStatus = status
            )
        }

        if (status == TrackerStatus.AD_ALLOWED) {
            return TrackerNetworksSection(
                descriptionRes = R.string.adLoadedSectionDescription,
                linkTextRes = R.string.adLoadedSectionLinkText,
                linkUrlRes = R.string.adLoadedSectionUrl,
                domain = domain,
                trackerStatus = status
            )
        }

        if (status == TrackerStatus.SITE_BREAKAGE_ALLOWED) {
            return TrackerNetworksSection(
                descriptionRes = R.string.domainsLoadedBreakageSectionDescription,
                trackerStatus = status
            )
        }

        if (status == TrackerStatus.ALLOWED) {
            return TrackerNetworksSection(
                descriptionRes = R.string.domainsLoadedSectionDescription,
                trackerStatus = status
            )
        }

        if (status == TrackerStatus.USER_ALLOWED) {
            return TrackerNetworksSection(
                descriptionRes = R.string.trackersBlockedNoSectionDescription,
                trackerStatus = status
            )
        }

        if (status == TrackerStatus.SAME_ENTITY_ALLOWED) {
            return TrackerNetworksSection(
                descriptionRes = R.string.domainsLoadedAssociatedSectionDescription,
                trackerStatus = status,
                domain = domain
            )
        }

        return null
    }
}
