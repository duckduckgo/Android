/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.privacy.db

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.common.test.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class UserAllowListRepositoryTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var dao: UserAllowListDao
    private lateinit var repository: UserAllowListRepository

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.userAllowListDao()
        repository = RealUserAllowListRepository(dao, TestScope(), coroutineRule.testDispatcherProvider, isMainProcess = true)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenDbContainsUserAllowListedDomainsThenUpdateUserAllowList() {
        assertEquals(0, repository.domainsInUserAllowList().size)
        dao.insert("example.com")
        assertEquals(1, repository.domainsInUserAllowList().size)
        assertEquals("example.com", repository.domainsInUserAllowList().first())
    }

    @Test
    fun whenDbContainsUserAllowListedDomainThenIsUrlInAllowListReturnsTrue() {
        dao.insert("example.com")
        assertTrue(repository.isUrlInUserAllowList("https://example.com"))
    }

    @Test
    fun whenDbDoesNotContainUserAllowListedDomainThenIsUrlInAllowListReturnsFalse() {
        dao.insert("example.com")
        assertFalse(repository.isUrlInUserAllowList("https://foo.com"))
    }

    @Test
    fun whenDomainIsAddedToUserAllowListThenItGetsInsertedIntoDb() = runTest {
        assertFalse(dao.contains("example.com"))
        repository.addDomainToUserAllowList("example.com")
        assertTrue(dao.contains("example.com"))
    }

    @Test
    fun whenDomainIsRemovedFromUserAllowListThenItGetsDeletedFromDb() = runTest {
        dao.insert("example.com")
        assertTrue(dao.contains("example.com"))
        repository.removeDomainFromUserAllowList("example.com")
        assertFalse(dao.contains("example.com"))
    }

    @Test
    fun whenAllowlistIsModifiedThenFlowEmitsEvent() = runTest {
        repository.domainsInUserAllowListFlow()
            .test {
                assertEquals(emptyList<String>(), awaitItem())

                repository.addDomainToUserAllowList("flowdomain.com")
                assertEquals(listOf("flowdomain.com"), awaitItem())

                repository.removeDomainFromUserAllowList("flowdomain.com")
                assertEquals(emptyList<String>(), awaitItem())

                cancelAndConsumeRemainingEvents()
            }
    }
}
