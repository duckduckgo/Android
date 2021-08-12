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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.runBlocking
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.Companion.ACTIVE_VARIANTS
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalCoroutinesApi
class AppUserEventsRepositoryTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val userEventsDependencies = UserEventsDependencies(context, coroutineRule.testDispatcherProvider)

    private val mockVariantManager: VariantManager = userEventsDependencies.variantManager

    private val mockfaviconManager: FaviconManager = userEventsDependencies.faviconManager

    private val db = userEventsDependencies.db

    private val userEventsStore = userEventsDependencies.userEventsStore

    private val userStageStore = userEventsDependencies.userStageStore

    private val favoritesRepository = userEventsDependencies.favoritesRepository

    private val testee = userEventsDependencies.userEventsRepository


    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenUserEventExistsThenEventNotNull() = coroutineRule.runBlocking {
        givenUserEventExists(UserEventKey.FIRST_NON_SERP_VISITED_SITE)

        assertNotNull(testee.getUserEvent(UserEventKey.FIRST_NON_SERP_VISITED_SITE))
    }

    @Test
    fun whenUserEventDoesNotExistThenEventNull() = coroutineRule.runBlocking {
        assertNull(testee.getUserEvent(UserEventKey.FIRST_NON_SERP_VISITED_SITE))
    }


    @Test
    fun firstTimeUserVisitsANonSERPSiteDuringOnboardingThenSiteStored() = coroutineRule.runBlocking {
        givenFavoritesOnboarindExpEnabled()
        givenOnboardingActive()

        testee.siteVisited("1", "http://example.com", "example.com")

        assertTrue(testee.getUserEvent(UserEventKey.FIRST_NON_SERP_VISITED_SITE)!!.payload.contains("http://example.com"))
    }

    @Test
    fun whenUserAlreadyVisitedASiteThenSiteNotStored() = coroutineRule.runBlocking {
        givenFavoritesOnboarindExpEnabled()
        givenOnboardingActive()

        testee.siteVisited("1", "http://example.com", "example.com")
        testee.siteVisited("1", "http://example2.com", "example2.com")

        assertFalse(testee.getUserEvent(UserEventKey.FIRST_NON_SERP_VISITED_SITE)!!.payload.contains("http://example2.com"))
    }

    @Test
    fun whenUserVisitedANonSERPSiteThenFaviconStored() = coroutineRule.runBlocking {
        givenFavoritesOnboarindExpEnabled()
        givenOnboardingActive()

        testee.siteVisited("1", "http://example.com", "example.com")

        verify(mockfaviconManager).persistCachedFavicon("1", "http://example.com")
    }

    @Test
    fun whenUserVisitsANonSERPSiteWhenOnboardingCompletedThenEventNotStored() = coroutineRule.runBlocking {
        givenFavoritesOnboarindExpEnabled()
        givenOnboardingCompleted()

        testee.siteVisited("1", "http://example.com", "example.com")

        assertNull(testee.getUserEvent(UserEventKey.FIRST_NON_SERP_VISITED_SITE))
    }

    @Test
    fun whenUserVisitedSERPThenSiteNotStored() = coroutineRule.runBlocking {
        givenFavoritesOnboarindExpEnabled()
        givenOnboardingActive()

        testee.siteVisited("1", "https://duckduckgo.com/?q=hola", "DuckDuckgo")

        assertNull(testee.getUserEvent(UserEventKey.FIRST_NON_SERP_VISITED_SITE))
    }

    @Test
    fun whenMoveVisitedSiteAsFavoriteIfUserEventExistsThenFavoriteCreated() = coroutineRule.runBlocking {
        givenFavoritesOnboarindExpEnabled()
        givenOnboardingActive()
        testee.siteVisited("1", "http://example.com", "example.com")

        testee.moveVisitedSiteAsFavorite()

        favoritesRepository.favorites().test {
            assertNotNull(expectItem().find { it.url == "http://example.com" })
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenMoveVisitedSiteAsFavoriteIfUserEventExistsThenUserEventCleared() = coroutineRule.runBlocking {
        givenFavoritesOnboarindExpEnabled()
        givenOnboardingActive()
        testee.siteVisited("1", "http://example.com", "example.com")

        testee.moveVisitedSiteAsFavorite()

        val visitedSite = testee.getUserEvent(UserEventKey.FIRST_NON_SERP_VISITED_SITE)!!
        assertNotNull(visitedSite)
        assertTrue(visitedSite.timestamp == 0L)
        assertTrue(visitedSite.payload.isEmpty())
    }

    @Test
    fun whenClearVisitedSiteThenDataRemoved() = coroutineRule.runBlocking {
        givenFavoritesOnboarindExpEnabled()
        givenOnboardingActive()
        testee.siteVisited("1", "http://example.com", "example.com")

        testee.clearVisitedSite()

        val visitedSite = testee.getUserEvent(UserEventKey.FIRST_NON_SERP_VISITED_SITE)!!
        assertNotNull(visitedSite)
        assertTrue(visitedSite.timestamp == 0L)
        assertTrue(visitedSite.payload.isEmpty())
    }

    @Test
    fun whenClearVisitedSiteThenFaviconRemoved() = coroutineRule.runBlocking {
        givenFavoritesOnboarindExpEnabled()
        givenOnboardingActive()
        testee.siteVisited("1", "http://example.com", "example.com")

        testee.clearVisitedSite()

        verify(mockfaviconManager).persistCachedFavicon("1", "http://example.com")
    }

    @Test
    fun whenNoSiteStoredThenVisitedSiteQueryReturns0() = coroutineRule.runBlocking {
        assertTrue(testee.visitedSiteByDomain("%example.com%") == 0)
    }

    @Test
    fun whenDomainQueryMatchesSiteStoredThenVisitedSiteQueryReturns1() = coroutineRule.runBlocking {
        givenFavoritesOnboarindExpEnabled()
        givenOnboardingActive()
        testee.siteVisited("1", "http://example.com", "example.com")

        assertTrue(testee.visitedSiteByDomain("%example.com%") == 1)
    }

    private suspend fun givenOnboardingActive() {
        userStageStore.moveToStage(AppStage.DAX_ONBOARDING)
    }

    private suspend fun givenOnboardingCompleted() {
        userStageStore.moveToStage(AppStage.ESTABLISHED)
    }

    private fun givenFavoritesOnboarindExpEnabled() {
        whenever(mockVariantManager.getVariant()).thenReturn(ACTIVE_VARIANTS.first { it.key == "po" })
    }

    private suspend fun givenUserEventExists(userEventKey: UserEventKey) {
        userEventsStore.registerUserEvent(userEventKey)
    }
}