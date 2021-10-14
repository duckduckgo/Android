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
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Practices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.GOOD
import com.duckduckgo.app.privacy.model.TestEntity
import com.duckduckgo.app.runBlocking
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private var userWhitelistDao: UserWhitelistDao = mock()
    private var contentBlocking: ContentBlocking = mock()

    private lateinit var testee: ScorecardViewModel

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun before() {
        testee = ScorecardViewModel(userWhitelistDao, contentBlocking, coroutineRule.testDispatcherProvider)

        whenever(userWhitelistDao.contains(any())).thenReturn(true)
    }

    @Test
    fun whenNoDataThenDefaultValuesAreUsed() = coroutineTestRule.runBlocking {
        testee.viewState().test {
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
    fun whenSiteGradesAreUpdatedThenViewModelGradesAreUpdated() = coroutineTestRule.runBlocking {
        val site = site(grade = PrivacyGrade.D, improvedGrade = PrivacyGrade.B)
        testee.onSiteChanged(site)
        testee.viewState().test {
            val viewState = awaitItem()
            assertEquals(PrivacyGrade.D, viewState.beforeGrade)
            assertEquals(PrivacyGrade.B, viewState.afterGrade)
        }
    }

    @Test
    fun whenSiteHttpsStatusIsUpdatedThenViewModelIsUpdated() = coroutineTestRule.runBlocking {
        testee.onSiteChanged(site(https = HttpsStatus.MIXED))
        testee.viewState().test {
            assertEquals(HttpsStatus.MIXED, awaitItem().httpsStatus)
        }
    }

    @Test
    fun whenTrackerCountIsUpdatedThenCountIsUpdatedInViewModel() = coroutineTestRule.runBlocking {
        testee.onSiteChanged(site(trackerCount = 10))
        testee.viewState().test {
            assertEquals(10, awaitItem().trackerCount)
        }
    }

    @Test
    fun whenMajorNetworkCountIsUpdatedThenCountIsUpdatedInViewModel() = coroutineTestRule.runBlocking {
        testee.onSiteChanged(site(majorNetworkCount = 10))
        testee.viewState().test {
            assertEquals(10, awaitItem().majorNetworkCount)
        }
    }

    @Test
    fun whenAllBlockedUpdatedToFalseThenViewModelIsUpdated() = coroutineTestRule.runBlocking {
        testee.onSiteChanged(site(allTrackersBlocked = false))
        testee.viewState().test {
            assertEquals(false, awaitItem().allTrackersBlocked)
        }
    }

    @Test
    fun whenAllBlockedUpdatedToTrueThenViewModelIsUpdated() = coroutineTestRule.runBlocking {
        testee.onSiteChanged(site(allTrackersBlocked = true))
        testee.viewState().test {
            assertEquals(true, awaitItem().allTrackersBlocked)
        }
    }

    @Test
    fun whenTermsAreUpdatedThenPracticesAreUpdatedInViewModel() = coroutineTestRule.runBlocking {
        val privacyPractices = Practices(0, GOOD, listOf("good"), listOf())
        testee.onSiteChanged(site(privacyPractices = privacyPractices))
        testee.viewState().test {
            assertEquals(GOOD, awaitItem().practices)
        }
    }

    @Test
    fun whenIsMemberOfMajorNetworkThenShowIsMemberOfMajorNetworkIsTrue() = coroutineTestRule.runBlocking {
        val site = site(entity = TestEntity("", "", 10.0))
        testee.onSiteChanged(site)
        testee.viewState().test {
            assertTrue(awaitItem().showIsMemberOfMajorNetwork)
        }
    }

    @Test
    fun whenIsNotMemberOfMajorNetworkThenShowIsMemberOfMajorNetworkIsFalse() = coroutineTestRule.runBlocking {
        val site = site(entity = TestEntity("", "", 0.0))
        testee.onSiteChanged(site)
        testee.viewState().test {
            assertFalse(awaitItem().showIsMemberOfMajorNetwork)
        }
    }

    @Test
    fun whenIsNotMemberOfAnyNetworkThenShowIsMemberOfMajorNetworkIsFalse() = coroutineTestRule.runBlocking {
        val site = site(entity = null)
        testee.onSiteChanged(site)
        testee.viewState().test {
            assertFalse(awaitItem().showIsMemberOfMajorNetwork)
        }
    }

    @Test
    fun whenSiteHasDifferentBeforeAndImprovedGradeThenShowEnhancedGradeIsTrue() = coroutineTestRule.runBlocking {
        val site = site(grade = PrivacyGrade.D, improvedGrade = PrivacyGrade.A)
        testee.onSiteChanged(site)
        testee.viewState().test {
            assertTrue(awaitItem().showEnhancedGrade)
        }
    }

    @Test
    fun whenSiteHasSameBeforeAndImprovedGradeThenShowEnhancedGradeIsFalse() = coroutineTestRule.runBlocking {
        val site = site(allTrackersBlocked = true, trackerCount = 0)
        testee.onSiteChanged(site)
        testee.viewState().test {
            assertFalse(awaitItem().showEnhancedGrade)
        }
    }

    @Test
    fun whenOnSiteChangedAndSiteIsInContentBlockingExceptionListThenReturnTrue() = coroutineTestRule.runBlocking {
        whenever(contentBlocking.isAnException(any())).thenReturn(true)
        val site = site(grade = PrivacyGrade.D, improvedGrade = PrivacyGrade.B)
        testee.onSiteChanged(site)
        testee.viewState().test {
            assertTrue(awaitItem().isSiteInTempAllowedList)
        }
    }

    @Test
    fun whenOnSiteChangedAndSiteIsInContentBlockingExceptionListThenPrivacyOnIsFalse() = coroutineTestRule.runBlocking {
        whenever(userWhitelistDao.contains(any())).thenReturn(true)
        whenever(contentBlocking.isAnException(any())).thenReturn(false)
        val site = site(grade = PrivacyGrade.D, improvedGrade = PrivacyGrade.B)
        testee.onSiteChanged(site)
        testee.viewState().test {
            assertFalse(awaitItem().privacyOn)
        }
    }

    @Test
    fun whenOnSiteChangedAndSiteIsNotInContentBlockingExceptionListThenPrivacyOnIsFalse() = coroutineTestRule.runBlocking {
        whenever(userWhitelistDao.contains(any())).thenReturn(false)
        whenever(contentBlocking.isAnException(any())).thenReturn(true)
        val site = site(grade = PrivacyGrade.D, improvedGrade = PrivacyGrade.B)
        testee.onSiteChanged(site)
        testee.viewState().test {
            assertFalse(awaitItem().privacyOn)
        }
    }

    @Test
    fun whenOnSiteChangedAndSiteIsNotInUserAllowListAndNotInContentBlockingExceptionListThenPrivacyOnIsTrue() = coroutineTestRule.runBlocking {
        whenever(userWhitelistDao.contains(any())).thenReturn(false)
        whenever(contentBlocking.isAnException(any())).thenReturn(false)
        val site = site(grade = PrivacyGrade.D, improvedGrade = PrivacyGrade.B)
        testee.onSiteChanged(site)
        testee.viewState().test {
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
