/*
 * Copyright (c) 2018 DuckDuckGo
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

import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacy.model.TestEntity
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import java.util.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class TrackerNetworksAdapterTest {

    private val mockListener: TrackerNetworksListener = mock()
    private val testee = TrackerNetworksAdapter(mockListener)

    @Test
    fun whenInitializedThenCountIsZero() {
        assertEquals(0, testee.itemCount)
    }

    @Test
    fun whenDataContainsEntriesThenCountIncludesOneForEachHeaderAndRow() {
        testee.updateData(data())
        assertEquals(7, testee.itemCount)
    }

    @Test
    fun whenDataContainsEntriesThenCorrectElementsAreCreated() {
        testee.updateData(data())
        assertEquals(TrackerNetworksAdapter.SECTION_TITLE, testee.getItemViewType(0))
        assertEquals(TrackerNetworksAdapter.HEADER, testee.getItemViewType(1))
        assertEquals(TrackerNetworksAdapter.ROW, testee.getItemViewType(2))
        assertEquals(TrackerNetworksAdapter.ROW, testee.getItemViewType(3))
        assertEquals(TrackerNetworksAdapter.HEADER, testee.getItemViewType(4))
        assertEquals(TrackerNetworksAdapter.ROW, testee.getItemViewType(5))
        assertEquals(TrackerNetworksAdapter.ROW, testee.getItemViewType(6))
    }

    @Test
    fun whenDataContainsOneGenericSectionThenDescriptionIsOverridden() {
        val genericDescriptionRes = R.string.domainsLoadedSectionDescription
        val expectedOverriddenDescriptionRes = R.string.trackersBlockedNoSectionDescription

        val result = testee.generateViewData(oneSectionData(genericDescriptionRes))

        assertEquals(3, result.size)
        assertTrue(result[0] is TrackerNetworksAdapter.ViewData.SectionTitle)
        assertEquals(expectedOverriddenDescriptionRes, (result[0] as (TrackerNetworksAdapter.ViewData.SectionTitle)).descriptionRes)
    }

    @Test
    fun whenDataContainsOneNonGenericSectionThenDescriptionIsNotOverridden() {
        val adDescriptionRes = R.string.adLoadedSectionDescription

        val result = testee.generateViewData(oneSectionData(adDescriptionRes))

        assertEquals(3, result.size)
        assertTrue(result[0] is TrackerNetworksAdapter.ViewData.SectionTitle)
        assertEquals(adDescriptionRes, (result[0] as (TrackerNetworksAdapter.ViewData.SectionTitle)).descriptionRes)
    }

    @Test
    fun whenDataContainsTwoSectionsWithOneGenericSectionDataThenDescriptionIsNotOverridden() {
        val expectedFirstDescriptionRes = R.string.domainsLoadedSectionDescription
        val expectedSecondDescriptionRes = R.string.domainsLoadedBreakageSectionDescription

        val result = testee.generateViewData(twoSectionsWithOneGenericSectionData())

        assertEquals(6, result.size)
        assertTrue(result[0] is TrackerNetworksAdapter.ViewData.SectionTitle)
        assertEquals(expectedSecondDescriptionRes, (result[0] as (TrackerNetworksAdapter.ViewData.SectionTitle)).descriptionRes)
        assertTrue(result[3] is TrackerNetworksAdapter.ViewData.SectionTitle)
        assertEquals(expectedFirstDescriptionRes, (result[3] as (TrackerNetworksAdapter.ViewData.SectionTitle)).descriptionRes)
    }

    private fun oneSectionData(descriptionRes: Int): SortedMap<TrackerNetworksSection, SortedMap<Entity, List<TrackingEvent>>> {
        val trackingEvent = TrackingEvent(
            documentUrl = "",
            trackerUrl = "",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.ALLOWED,
            type = TrackerType.OTHER,
        )

        val section = TrackerNetworksSection(descriptionRes, 2, null, TrackerStatus.ALLOWED)
        val entitiesMap = mapOf(
            TestEntity("A", "A", 0.0) as Entity to listOf(trackingEvent),
        ).toSortedMap(compareBy { it.displayName })

        return mapOf(section to entitiesMap).toSortedMap(compareBy { it.trackerStatus })
    }

    private fun twoSectionsWithOneGenericSectionData(): SortedMap<TrackerNetworksSection, SortedMap<Entity, List<TrackingEvent>>> {
        val trackingEvent = TrackingEvent(
            documentUrl = "",
            trackerUrl = "",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.ALLOWED,
            type = TrackerType.OTHER,
        )

        val firstSection = TrackerNetworksSection(R.string.domainsLoadedSectionDescription, 2, null, TrackerStatus.ALLOWED)
        val secondSection = TrackerNetworksSection(R.string.domainsLoadedBreakageSectionDescription, 2, null, TrackerStatus.SITE_BREAKAGE_ALLOWED)
        val firstEntitiesMap = mapOf(
            TestEntity("A", "A", 0.0) as Entity to listOf(trackingEvent),
        ).toSortedMap(compareBy { it.displayName })
        val secondEntitiesMap = mapOf(
            TestEntity("B", "B", 0.0) as Entity to listOf(trackingEvent),
        ).toSortedMap(compareBy { it.displayName })

        return mapOf(firstSection to firstEntitiesMap, secondSection to secondEntitiesMap).toSortedMap(compareBy { it.trackerStatus })
    }

    private fun data(): SortedMap<TrackerNetworksSection, SortedMap<Entity, List<TrackingEvent>>> {
        val trackingEvent = TrackingEvent(
            documentUrl = "",
            trackerUrl = "",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.BLOCKED,
            type = TrackerType.OTHER,
        )
        val minorNetworkList = listOf(trackingEvent, trackingEvent)
        val majorNetworkList = listOf(trackingEvent, trackingEvent)

        val section = TrackerNetworksSection(1, 2, null, TrackerStatus.ALLOWED)
        val entitiesMap = mapOf(
            TestEntity("A", "A", 0.0) as Entity to minorNetworkList,
            TestEntity("B", "B", 0.0) as Entity to majorNetworkList,
        ).toSortedMap(compareBy { it.displayName })

        return mapOf(section to entitiesMap).toSortedMap(compareBy { it.trackerStatus })
    }
}
