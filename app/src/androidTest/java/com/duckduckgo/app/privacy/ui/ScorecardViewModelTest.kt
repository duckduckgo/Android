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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Practices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.GOOD
import com.duckduckgo.app.privacy.model.TestEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.privacy.config.api.ContentBlocking
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalCoroutinesApi
class ScorecardViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val tabRepository: TabRepository = mock()
    private var userWhitelistDao: UserWhitelistDao = mock()
    private var contentBlocking: ContentBlocking = mock()

    private lateinit var testee: ScorecardViewModel

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun before() {
        testee = ScorecardViewModel(tabRepository, userWhitelistDao, contentBlocking, coroutineRule.testDispatcherProvider)

        whenever(userWhitelistDao.contains(any())).thenReturn(true)
    }

    @Test
    fun whenNoDataThenDefaultValuesAreUsed() = runTest {
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(MutableLiveData())

        testee.scoreCard("1").test {
            val viewState = awaitItem()
            assertEquals("", viewState.domain)
            assertEquals(PrivacyGrade.UNKNOWN, viewState.beforeGrade)
            assertEquals(PrivacyGrade.UNKNOWN, viewState.afterGrade)
            assertEquals(HttpsStatus.SECURE, viewState.httpsStatus)
            assertEquals(0, viewState.trackerCount)
            assertEquals(0, viewState.majorNetworkCount)
            assertTrue(viewState.allTrackersBlocked)
            assertFalse(viewState.showIsMemberOfMajorNetwork)
            assertFalse(viewState.showEnhancedGrade)
            assertEquals(PrivacyPractices.Summary.UNKNOWN, viewState.practices)
            assertFalse(viewState.isSiteInTempAllowedList)
        }
    }

    @Test
    fun whenSiteGradesAreUpdatedThenViewModelGradesAreUpdated() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(grade = PrivacyGrade.D, improvedGrade = PrivacyGrade.B))

        testee.scoreCard("1").test {
            val viewState = awaitItem()
            assertEquals(PrivacyGrade.D, viewState.beforeGrade)
            assertEquals(PrivacyGrade.B, viewState.afterGrade)
        }
    }

    @Test
    fun whenSiteHttpsStatusIsUpdatedThenViewModelIsUpdated() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(https = HttpsStatus.MIXED))

        testee.scoreCard("1").test {
            assertEquals(HttpsStatus.MIXED, awaitItem().httpsStatus)
        }
    }

    @Test
    fun whenTrackerCountIsUpdatedThenCountIsUpdatedInViewModel() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(trackerCount = 10))

        testee.scoreCard("1").test {
            assertEquals(10, awaitItem().trackerCount)
        }
    }

    @Test
    fun whenMajorNetworkCountIsUpdatedThenCountIsUpdatedInViewModel() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(majorNetworkCount = 10))

        testee.scoreCard("1").test {
            assertEquals(10, awaitItem().majorNetworkCount)
        }
    }

    @Test
    fun whenAllBlockedUpdatedToFalseThenViewModelIsUpdated() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(allTrackersBlocked = false))

        testee.scoreCard("1").test {
            assertEquals(false, awaitItem().allTrackersBlocked)
        }
    }

    @Test
    fun whenAllBlockedUpdatedToTrueThenViewModelIsUpdated() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(allTrackersBlocked = true))

        testee.scoreCard("1").test {
            assertEquals(true, awaitItem().allTrackersBlocked)
        }
    }

    @Test
    fun whenTermsAreUpdatedThenPracticesAreUpdatedInViewModel() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        val privacyPractices = Practices(0, GOOD, listOf("good"), listOf())
        siteData.postValue(site(privacyPractices = privacyPractices))

        testee.scoreCard("1").test {
            assertEquals(GOOD, awaitItem().practices)
        }
    }

    @Test
    fun whenIsMemberOfMajorNetworkThenShowIsMemberOfMajorNetworkIsTrue() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(entity = TestEntity(name = "", displayName = "", prevalence = 10.0)))

        testee.scoreCard("1").test {
            assertTrue(awaitItem().showIsMemberOfMajorNetwork)
        }
    }

    @Test
    fun whenIsNotMemberOfMajorNetworkThenShowIsMemberOfMajorNetworkIsFalse() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(entity = TestEntity(name = "", displayName = "", prevalence = 0.0)))

        testee.scoreCard("1").test {
            assertFalse(awaitItem().showIsMemberOfMajorNetwork)
        }
    }

    @Test
    fun whenIsNotMemberOfAnyNetworkThenShowIsMemberOfMajorNetworkIsFalse() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(entity = null))

        testee.scoreCard("1").test {
            assertFalse(awaitItem().showIsMemberOfMajorNetwork)
        }
    }

    @Test
    fun whenSiteHasDifferentBeforeAndImprovedGradeThenShowEnhancedGradeIsTrue() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(grade = PrivacyGrade.D, improvedGrade = PrivacyGrade.A))

        testee.scoreCard("1").test {
            assertTrue(awaitItem().showEnhancedGrade)
        }
    }

    @Test
    fun whenSiteHasSameBeforeAndImprovedGradeThenShowEnhancedGradeIsFalse() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)

        siteData.postValue(site(allTrackersBlocked = true, trackerCount = 0))

        testee.scoreCard("1").test {
            assertFalse(awaitItem().showEnhancedGrade)
        }
    }

    @Test
    fun whenOnSiteChangedAndSiteIsInContentBlockingExceptionListThenReturnTrue() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)
        whenever(contentBlocking.isAnException(any())).thenReturn(true)

        siteData.postValue(site(grade = PrivacyGrade.D, improvedGrade = PrivacyGrade.B))

        testee.scoreCard("1").test {
            assertTrue(awaitItem().isSiteInTempAllowedList)
        }
    }

    @Test
    fun whenOnSiteChangedAndSiteIsInContentBlockingExceptionListThenPrivacyOnIsFalse() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)
        whenever(userWhitelistDao.contains(any())).thenReturn(true)
        whenever(contentBlocking.isAnException(any())).thenReturn(false)

        siteData.postValue(site(grade = PrivacyGrade.D, improvedGrade = PrivacyGrade.B))

        testee.scoreCard("1").test {
            assertFalse(awaitItem().privacyOn)
        }
    }

    @Test
    fun whenOnSiteChangedAndSiteIsNotInContentBlockingExceptionListThenPrivacyOnIsFalse() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)
        whenever(userWhitelistDao.contains(any())).thenReturn(false)
        whenever(contentBlocking.isAnException(any())).thenReturn(true)

        siteData.postValue(site(grade = PrivacyGrade.D, improvedGrade = PrivacyGrade.B))

        testee.scoreCard("1").test {
            assertFalse(awaitItem().privacyOn)
        }
    }

    @Test
    fun whenOnSiteChangedAndSiteIsNotInUserAllowListAndNotInContentBlockingExceptionListThenPrivacyOnIsTrue() = runTest {
        val siteData = MutableLiveData<Site>()
        whenever(tabRepository.retrieveSiteData(any())).thenReturn(siteData)
        whenever(userWhitelistDao.contains(any())).thenReturn(false)
        whenever(contentBlocking.isAnException(any())).thenReturn(false)

        siteData.postValue(site(grade = PrivacyGrade.D, improvedGrade = PrivacyGrade.B))

        testee.scoreCard("1").test {
            assertTrue(awaitItem().privacyOn)
        }
    }

    private fun site(
        https: HttpsStatus = HttpsStatus.SECURE,
        trackerCount: Int = 0,
        majorNetworkCount: Int = 0,
        allTrackersBlocked: Boolean = true,
        privacyPractices: Practices = PrivacyPractices.UNKNOWN,
        entity: Entity? = null,
        grade: PrivacyGrade = PrivacyGrade.UNKNOWN,
        improvedGrade: PrivacyGrade = PrivacyGrade.UNKNOWN
    ): Site {
        val site: Site = mock()
        whenever(site.https).thenReturn(https)
        whenever(site.entity).thenReturn(entity)
        whenever(site.trackerCount).thenReturn(trackerCount)
        whenever(site.majorNetworkCount).thenReturn(majorNetworkCount)
        whenever(site.allTrackersBlocked).thenReturn(allTrackersBlocked)
        whenever(site.privacyPractices).thenReturn(privacyPractices)
        whenever(site.calculateGrades()).thenReturn(Site.SiteGrades(grade, improvedGrade))
        return site
    }
}
