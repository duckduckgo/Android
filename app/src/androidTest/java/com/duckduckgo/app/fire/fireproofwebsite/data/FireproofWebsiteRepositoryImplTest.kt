/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.fire.fireproofwebsite.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.blockingObserve
import dagger.Lazy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class FireproofWebsiteRepositoryImplTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var fireproofWebsiteDao: FireproofWebsiteDao
    private lateinit var fireproofWebsiteRepositoryImpl: FireproofWebsiteRepositoryImpl
    private val mockFaviconManager: FaviconManager = mock()
    private val lazyFaviconManager = Lazy { mockFaviconManager }

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fireproofWebsiteDao = db.fireproofWebsiteDao()
        fireproofWebsiteRepositoryImpl = FireproofWebsiteRepositoryImpl(
            db.fireproofWebsiteDao(),
            coroutineRule.testDispatcherProvider,
            lazyFaviconManager,
        )
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenFireproofWebsiteEmptyThenNoEntityIsCreated() = runTest {
        val fireproofWebsiteEntity = fireproofWebsiteRepositoryImpl.fireproofWebsite("")
        assertNull(fireproofWebsiteEntity)
    }

    @Test
    fun whenFireproofWebsiteInvalidThenNoEntityIsCreated() = runTest {
        val fireproofWebsiteEntity = fireproofWebsiteRepositoryImpl.fireproofWebsite("aa1111")
        assertNull(fireproofWebsiteEntity)
    }

    @Test
    fun whenFireproofWebsiteWithSchemaThenNoEntityIsCreated() = runTest {
        val fireproofWebsiteEntity = fireproofWebsiteRepositoryImpl.fireproofWebsite("https://aa1111.com")
        assertNull(fireproofWebsiteEntity)
    }

    @Test
    fun whenFireproofWebsiteIsValidThenEntityCreated() = runTest {
        val fireproofWebsiteEntity = fireproofWebsiteRepositoryImpl.fireproofWebsite("example.com")
        assertEquals(FireproofWebsiteEntity("example.com"), fireproofWebsiteEntity)
    }

    @Test
    fun whenGetAllFireproofWebsitesThenReturnLiveDataWithAllItemsFromDatabase() = runTest {
        givenFireproofWebsiteDomain("example.com", "example2.com")
        val fireproofWebsiteEntities = fireproofWebsiteRepositoryImpl.getFireproofWebsites().blockingObserve()!!
        assertEquals(fireproofWebsiteEntities.size, 2)
    }

    @Test
    fun whenRemoveFireproofWebsiteThenItemRemovedFromDatabase() = runTest {
        givenFireproofWebsiteDomain("example.com", "example2.com")

        fireproofWebsiteRepositoryImpl.removeFireproofWebsite(FireproofWebsiteEntity("example.com"))

        val fireproofWebsiteEntities = fireproofWebsiteDao.fireproofWebsitesSync()
        assertEquals(fireproofWebsiteEntities.size, 1)
    }

    @Test
    fun whenRemoveFireproofWebsiteThenDeletePersistedFavicon() = runTest {
        givenFireproofWebsiteDomain("example.com")

        fireproofWebsiteRepositoryImpl.removeFireproofWebsite(FireproofWebsiteEntity("example.com"))

        verify(mockFaviconManager).deletePersistedFavicon("example.com")
    }

    @Test
    fun whenFireproofWebsitesCountByDomainAndNoWebsitesMatchThenReturnZero() = runTest {
        givenFireproofWebsiteDomain("example.com")

        val count = fireproofWebsiteRepositoryImpl.fireproofWebsitesCountByDomain("test.com")

        assertEquals(0, count)
    }

    @Test
    fun whenFireproofWebsitesCountByDomainAndWebsitesMatchThenReturnCount() = runTest {
        givenFireproofWebsiteDomain("example.com")

        val count = fireproofWebsiteRepositoryImpl.fireproofWebsitesCountByDomain("example.com")

        assertEquals(1, count)
    }

    @Test
    fun whenSubDomainFireproofWebsiteThenExpectedListReturned() {
        givenFireproofWebsitesStored(FireproofWebsiteEntity("mobile.twitter.com"))
        val expectedList = listOf(
            ".mobile.twitter.com",
            "mobile.twitter.com",
            ".twitter.com",
            ".com",
        )

        val hostsToPreserve = fireproofWebsiteRepositoryImpl.fireproofWebsites()

        assertTrue(expectedList.all { hostsToPreserve.contains(it) })
    }

    @Test
    fun whenFireproofWebsiteThenExpectedListReturned() {
        givenFireproofWebsitesStored(FireproofWebsiteEntity("twitter.com"))
        val expectedList = listOf("twitter.com", ".twitter.com", ".com")

        val hostsToPreserve = fireproofWebsiteRepositoryImpl.fireproofWebsites()

        assertTrue(expectedList.all { hostsToPreserve.contains(it) })
    }

    @Test
    fun whenMultipleFireproofWebsiteWithSameTopLevelThenExpectedListReturned() {
        givenFireproofWebsitesStored(FireproofWebsiteEntity("twitter.com"))
        givenFireproofWebsitesStored(FireproofWebsiteEntity("example.com"))
        val expectedList = listOf(
            ".example.com",
            "example.com",
            "twitter.com",
            ".twitter.com",
            ".com",
        )

        val hostsToPreserve = fireproofWebsiteRepositoryImpl.fireproofWebsites()

        assertEquals(expectedList.size, hostsToPreserve.size)
        assertTrue(expectedList.all { hostsToPreserve.contains(it) })
    }

    private fun givenFireproofWebsitesStored(fireproofWebsiteEntity: FireproofWebsiteEntity) {
        fireproofWebsiteDao.insert(fireproofWebsiteEntity)
    }

    private fun givenFireproofWebsiteDomain(vararg fireproofWebsitesDomain: String) {
        fireproofWebsitesDomain.forEach {
            fireproofWebsiteDao.insert(FireproofWebsiteEntity(domain = it))
        }
    }
}
