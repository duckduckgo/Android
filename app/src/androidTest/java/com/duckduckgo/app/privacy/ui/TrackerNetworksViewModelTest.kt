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

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.model.TestEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalCoroutinesApi
@ExperimentalTime
class TrackerNetworksViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val tabRepository: TabRepository = mock()

    private lateinit var testee: TrackerNetworksViewModel

    @Before
    fun before() {
        testee = TrackerNetworksViewModel(tabRepository)
    }

    @Test
    fun whenNoDataThenDefaultValuesAreUsed() = runTest {
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(MutableLiveData())

        testee.trackers("1").test {
            val viewState = awaitItem()
            assertEquals("", viewState.domain)
            assertEquals(true, viewState.allTrackersBlocked)
            assertTrue(viewState.trackingEventsByNetwork.isEmpty())
        }
    }

    @Test
    fun whenUrlIsUpdatedThenViewModelDomainIsUpdated() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(url = "http://example.com/path"))

        testee.trackers("1").test {
            assertEquals("example.com", awaitItem().domain)
        }
    }

    @Test
    fun whenTrackersUpdatedWithNoTrackersThenViewModelListIsEmpty() = runTest {
        val input = listOf(TrackingEvent(Url.DOCUMENT, Url.tracker(1), null, Entity.MINOR_ENTITY_A, true, null))
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(trackingEvents = input))
        testee.trackers("1").test {
            assertTrue(awaitItem().trackingEventsByNetwork.isNotEmpty())
        }

        siteData.postValue(site(trackingEvents = emptyList()))
        testee.trackers("1").test {
            assertTrue(awaitItem().trackingEventsByNetwork.isEmpty())
        }
    }

    @Test
    fun whenTrackersUpdatedThenViewModelUpdatedWithDistinctEntitiesOrderedBy() = runTest {
        val input = listOf(
            // Minor entity with 3 distinct trackers
            TrackingEvent(Url.DOCUMENT, Url.tracker(1), null, Entity.MINOR_ENTITY_A, true, null),
            TrackingEvent(Url.DOCUMENT, Url.tracker(1), null, Entity.MINOR_ENTITY_A, true, null),
            TrackingEvent(Url.DOCUMENT, Url.tracker(2), null, Entity.MINOR_ENTITY_A, true, null),
            TrackingEvent(Url.DOCUMENT, Url.tracker(3), null, Entity.MINOR_ENTITY_A, true, null),

            // Minor entity with 1 distinct tracker
            TrackingEvent(Url.DOCUMENT, Url.tracker(4), null, Entity.MINOR_ENTITY_B, true, null),

            // Major entity with 2 distinct tracker
            TrackingEvent(Url.DOCUMENT, Url.tracker(6), null, Entity.MAJOR_ENTITY_B, true, null),
            TrackingEvent(Url.DOCUMENT, Url.tracker(6), null, Entity.MAJOR_ENTITY_B, true, null),
            TrackingEvent(Url.DOCUMENT, Url.tracker(7), null, Entity.MAJOR_ENTITY_B, true, null),

            // Major entity with 1 distinct tracker
            TrackingEvent(Url.DOCUMENT, Url.tracker(5), null, Entity.MAJOR_ENTITY_A, true, null)
        )
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(trackingEvents = input))
        testee.trackers("1").test {
            val result = awaitItem().trackingEventsByNetwork
            assertEquals(0, result.keys.indexOf(Entity.MAJOR_ENTITY_A))
            assertEquals(1, result.keys.indexOf(Entity.MAJOR_ENTITY_B))
            assertEquals(2, result.keys.indexOf(Entity.MINOR_ENTITY_A))
            assertEquals(3, result.keys.indexOf(Entity.MINOR_ENTITY_B))
            assertEquals(1, result[Entity.MAJOR_ENTITY_A]?.count())
            assertEquals(2, result[Entity.MAJOR_ENTITY_B]?.count())
            assertEquals(3, result[Entity.MINOR_ENTITY_A]?.count())
            assertEquals(1, result[Entity.MINOR_ENTITY_B]?.count())
        }
    }

    private fun site(
        url: String = "",
        trackingEvents: List<TrackingEvent> = emptyList()
    ): Site {
        val site: Site = mock()
        whenever(site.url).thenReturn(url)
        whenever(site.uri).thenReturn(Uri.parse(url))
        whenever(site.trackingEvents).thenReturn(trackingEvents)
        return site
    }

    object Entity {
        val MINOR_ENTITY_A = TestEntity("Minor A", "Minor A", 0.0)
        val MINOR_ENTITY_B = TestEntity("Minor B", "Minor B", 0.0)
        val MAJOR_ENTITY_A = TestEntity("Major A", "Major A", 9.0)
        val MAJOR_ENTITY_B = TestEntity("Major B", "Major B", 9.0)
    }

    object Url {
        const val DOCUMENT = "http://document.com"
        const val TRACKER = "http://tracker%d.com"

        fun tracker(number: Int): String = String.format(TRACKER, number)
    }
}
