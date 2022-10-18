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
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.model.TestEntity
import com.duckduckgo.app.privacy.ui.TrackerNetworksViewModel.ViewState.DomainsViewState
import com.duckduckgo.app.privacy.ui.TrackerNetworksViewModel.ViewState.TrackersViewState
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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
    fun whenNoTrackersDataThenDefaultValuesAreUsed() = runTest {
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(MutableLiveData())

        testee.trackers(tabId = "1", domainsLoaded = false).test {
            val viewState = awaitItem() as TrackersViewState
            assertEquals("", viewState.domain)
            assertEquals(true, viewState.allTrackersBlocked)
            assertTrue(viewState.eventsByNetwork.isEmpty())
        }
    }

    @Test
    fun whenNoDomainsLoadedDataThenDefaultValuesAreUsed() = runTest {
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(MutableLiveData())

        testee.trackers(tabId = "1", domainsLoaded = true).test {
            val viewState = awaitItem() as DomainsViewState
            assertEquals("", viewState.domain)
            assertEquals(0, viewState.count)
            assertTrue(viewState.eventsByNetwork.isEmpty())
        }
    }

    @Test
    fun whenUrlIsUpdatedThenViewModelDomainIsUpdated() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(url = "http://example.com/path"))

        testee.trackers("1", false).test {
            assertEquals("example.com", awaitItem().domain)
        }
    }

    @Test
    fun whenTrackersUpdatedWithNoTrackersThenViewModelListIsEmpty() = runTest {
        val input = listOf(
            TrackingEvent(
                documentUrl = Url.DOCUMENT,
                trackerUrl = Url.tracker(1),
                categories = null,
                entity = DummyEntity.MINOR_ENTITY_A,
                surrogateId = null,
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
            ),
        )
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(trackingEvents = input))
        testee.trackers("1", false).test {
            assertTrue(awaitItem().eventsByNetwork.isNotEmpty())
        }

        siteData.postValue(site(trackingEvents = emptyList()))
        testee.trackers("1", false).test {
            assertTrue(awaitItem().eventsByNetwork.isEmpty())
        }
    }

    @Test
    fun whenTrackersUpdatedThenViewModelUpdatedWithDistinctEntitiesOrderedBy() = runTest {
        val input = listOf(
            // Minor entity with 3 distinct trackers
            TrackingEvent(Url.DOCUMENT, Url.tracker(1), null, DummyEntity.MINOR_ENTITY_A, null, TrackerStatus.BLOCKED, TrackerType.OTHER),
            TrackingEvent(Url.DOCUMENT, Url.tracker(1), null, DummyEntity.MINOR_ENTITY_A, null, TrackerStatus.BLOCKED, TrackerType.OTHER),
            TrackingEvent(Url.DOCUMENT, Url.tracker(2), null, DummyEntity.MINOR_ENTITY_A, null, TrackerStatus.BLOCKED, TrackerType.OTHER),
            TrackingEvent(Url.DOCUMENT, Url.tracker(3), null, DummyEntity.MINOR_ENTITY_A, null, TrackerStatus.BLOCKED, TrackerType.OTHER),

            // Minor entity with 1 distinct tracker
            TrackingEvent(Url.DOCUMENT, Url.tracker(4), null, DummyEntity.MINOR_ENTITY_B, null, TrackerStatus.BLOCKED, TrackerType.OTHER),

            // Major entity with 2 distinct tracker
            TrackingEvent(Url.DOCUMENT, Url.tracker(6), null, DummyEntity.MAJOR_ENTITY_B, null, TrackerStatus.BLOCKED, TrackerType.OTHER),
            TrackingEvent(Url.DOCUMENT, Url.tracker(6), null, DummyEntity.MAJOR_ENTITY_B, null, TrackerStatus.BLOCKED, TrackerType.OTHER),
            TrackingEvent(Url.DOCUMENT, Url.tracker(7), null, DummyEntity.MAJOR_ENTITY_B, null, TrackerStatus.BLOCKED, TrackerType.OTHER),

            // Major entity with 1 distinct tracker
            TrackingEvent(Url.DOCUMENT, Url.tracker(5), null, DummyEntity.MAJOR_ENTITY_A, null, TrackerStatus.BLOCKED, TrackerType.OTHER),

            // 2 Ads
            TrackingEvent(Url.DOCUMENT, Url.tracker(8), null, DummyEntity.MAJOR_ENTITY_A, null, TrackerStatus.AD_ALLOWED, TrackerType.AD),
            TrackingEvent(Url.DOCUMENT, Url.tracker(9), null, DummyEntity.MAJOR_ENTITY_B, null, TrackerStatus.AD_ALLOWED, TrackerType.AD),

            // 1 Site breakage
            TrackingEvent(
                Url.DOCUMENT,
                Url.tracker(10),
                null,
                DummyEntity.MAJOR_ENTITY_A,
                null,
                TrackerStatus.SITE_BREAKAGE_ALLOWED,
                TrackerType.OTHER,
            ),
        )
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(trackingEvents = input))
        testee.trackers("1", false).test {
            val result = awaitItem().eventsByNetwork

            val section = result.keys.toList()
            assertEquals(1, section.size)

            section[0].let {
                assertNull(it.descriptionRes)
                assertEquals(TrackerStatus.BLOCKED, it.trackerStatus)

                val entitiesMap = result[it]
                val expected = listOf(
                    Pair(DummyEntity.MAJOR_ENTITY_A, 1),
                    Pair(DummyEntity.MAJOR_ENTITY_B, 2),
                    Pair(DummyEntity.MINOR_ENTITY_A, 3),
                    Pair(DummyEntity.MINOR_ENTITY_B, 1),
                )
                entitiesMap?.keys?.forEachIndexed { index, entity ->
                    assertEquals(expected[index].first, entity)
                    assertEquals(expected[index].second, entitiesMap[entity]?.count())
                }
            }
        }
    }

    @Test
    fun whenDomainsLoadedUpdatedWithNoDomainsThenViewModelListIsEmpty() = runTest {
        val input = listOf(
            TrackingEvent(
                documentUrl = Url.DOCUMENT,
                trackerUrl = Url.tracker(1),
                categories = null,
                entity = DummyEntity.MINOR_ENTITY_A,
                surrogateId = null,
                status = TrackerStatus.ALLOWED,
                type = TrackerType.OTHER,
            ),
        )
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(trackingEvents = input))
        testee.trackers(tabId = "1", domainsLoaded = true).test {
            assertTrue(awaitItem().eventsByNetwork.isNotEmpty())
        }

        siteData.postValue(site(trackingEvents = emptyList()))
        testee.trackers(tabId = "1", domainsLoaded = true).test {
            assertTrue(awaitItem().eventsByNetwork.isEmpty())
        }
    }

    @Test
    fun whenDomainsLoadedUpdatedThenViewModelUpdatedWithDistinctEntitiesOrderedBy() = runTest {
        val input = listOf(
            // 3 distinct domains loaded
            TrackingEvent(Url.DOCUMENT, Url.tracker(1), null, DummyEntity.MINOR_ENTITY_A, null, TrackerStatus.ALLOWED, TrackerType.OTHER),
            TrackingEvent(Url.DOCUMENT, Url.tracker(1), null, DummyEntity.MINOR_ENTITY_A, null, TrackerStatus.ALLOWED, TrackerType.OTHER),
            TrackingEvent(Url.DOCUMENT, Url.tracker(2), null, DummyEntity.MINOR_ENTITY_B, null, TrackerStatus.ALLOWED, TrackerType.OTHER),
            TrackingEvent(Url.DOCUMENT, Url.tracker(3), null, DummyEntity.MINOR_ENTITY_A, null, TrackerStatus.ALLOWED, TrackerType.OTHER),

            // 2 distinct trackers
            TrackingEvent(Url.DOCUMENT, Url.tracker(6), null, DummyEntity.MAJOR_ENTITY_B, null, TrackerStatus.BLOCKED, TrackerType.OTHER),
            TrackingEvent(Url.DOCUMENT, Url.tracker(6), null, DummyEntity.MAJOR_ENTITY_B, null, TrackerStatus.BLOCKED, TrackerType.OTHER),
            TrackingEvent(Url.DOCUMENT, Url.tracker(7), null, DummyEntity.MAJOR_ENTITY_B, null, TrackerStatus.BLOCKED, TrackerType.OTHER),

            // 1 domain loaded (ad)
            TrackingEvent(Url.DOCUMENT, Url.tracker(5), null, DummyEntity.MAJOR_ENTITY_A, null, TrackerStatus.AD_ALLOWED, TrackerType.AD),

            // 1 Site breakage
            TrackingEvent(
                Url.DOCUMENT,
                Url.tracker(10),
                null,
                DummyEntity.MAJOR_ENTITY_A,
                null,
                TrackerStatus.SITE_BREAKAGE_ALLOWED,
                TrackerType.OTHER,
            ),
        )
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(trackingEvents = input))
        testee.trackers(tabId = "1", domainsLoaded = true).test {
            val result = awaitItem().eventsByNetwork

            val section = result.keys.toList()
            assertEquals(3, section.size)

            section[0].let {
                assertEquals(R.string.adLoadedSectionDescription, it.descriptionRes)
                assertEquals(R.string.adLoadedSectionLinkText, it.linkTextRes)
                assertEquals(R.string.adLoadedSectionUrl, it.linkUrlRes)
                assertEquals(TrackerStatus.AD_ALLOWED, it.trackerStatus)
                assertNotNull(it.domain)

                val entitiesMap = result[it]
                val expected = listOf(
                    Pair(DummyEntity.MAJOR_ENTITY_A, 1),
                )
                entitiesMap?.keys?.forEachIndexed { index, entity ->
                    assertEquals(expected[index].first, entity)
                    assertEquals(expected[index].second, entitiesMap[entity]?.count())
                }
            }

            section[1].let {
                assertEquals(R.string.domainsLoadedBreakageSectionDescription, it.descriptionRes)
                assertEquals(TrackerStatus.SITE_BREAKAGE_ALLOWED, it.trackerStatus)

                val entitiesMap = result[it]
                val expected = listOf(
                    Pair(DummyEntity.MAJOR_ENTITY_A, 1),
                )
                entitiesMap?.keys?.forEachIndexed { index, entity ->
                    assertEquals(expected[index].first, entity)
                    assertEquals(expected[index].second, entitiesMap[entity]?.count())
                }
            }

            section[2].let {
                assertEquals(R.string.domainsLoadedSectionDescription, it.descriptionRes)
                assertEquals(TrackerStatus.ALLOWED, it.trackerStatus)

                val entitiesMap = result[it]
                val expected = listOf(
                    Pair(DummyEntity.MINOR_ENTITY_A, 2),
                    Pair(DummyEntity.MINOR_ENTITY_B, 1),
                )
                entitiesMap?.keys?.forEachIndexed { index, entity ->
                    assertEquals(expected[index].first, entity)
                    assertEquals(expected[index].second, entitiesMap[entity]?.count())
                }
            }
        }
    }

    private fun site(
        url: String = "",
        trackingEvents: List<TrackingEvent> = emptyList(),
    ): Site {
        val site: Site = mock()
        whenever(site.url).thenReturn(url)
        whenever(site.uri).thenReturn(Uri.parse(url))
        whenever(site.trackingEvents).thenReturn(trackingEvents)
        return site
    }

    object DummyEntity {
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
