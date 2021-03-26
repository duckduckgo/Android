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

package com.duckduckgo.app.browser.cookies.db

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class AllowedDomainsRepositoryTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var allowedDomainsDao: AllowedDomainsDao
    private lateinit var allowedDomainsRepository: AllowedDomainsRepository

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        allowedDomainsDao = db.allowedDomainsDao()
        allowedDomainsRepository = AllowedDomainsRepository(allowedDomainsDao, coroutineRule.testDispatcherProvider)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenAddDomainIfIsEmptyThenReturnNull() = coroutineRule.runBlocking {
        assertNull(allowedDomainsRepository.addDomain(""))
    }

    @Test
    fun whenAddDomainAndDomainNotValidThenReturnNull() = coroutineRule.runBlocking {
        assertNull(allowedDomainsRepository.addDomain("https://example.com"))
    }

    @Test
    fun whenAddValidDomainThenReturnNonNull() = coroutineRule.runBlocking {
        assertNotNull(allowedDomainsRepository.addDomain("example.com"))
    }

    @Test
    fun whenGetDomainIfDomainExistsThenReturnAllowedDomainEntity() = coroutineRule.runBlocking {
        givenAllowedDomain("example.com")

        val allowedDomainEntity = allowedDomainsRepository.getDomain("example.com")
        assertEquals("example.com", allowedDomainEntity?.domain)
    }

    @Test
    fun whenGetDomainIfDomainDoesNotExistThenReturnNull() = coroutineRule.runBlocking {
        val allowedDomainEntity = allowedDomainsRepository.getDomain("example.com")
        assertNull(allowedDomainEntity)
    }

    @Test
    fun whenRemoveDomainThenDomainDeletedFromDatabase() = coroutineRule.runBlocking {
        givenAllowedDomain("example.com")
        val allowedDomainEntity = allowedDomainsRepository.getDomain("example.com")

        allowedDomainsRepository.removeDomain(allowedDomainEntity!!)

        val deletedEntity = allowedDomainsRepository.getDomain("example.com")
        assertNull(deletedEntity)
    }

    @Test
    fun whenDeleteAllThenAllDomainsDeletedExceptFromTheExceptionList() = coroutineRule.runBlocking {
        givenAllowedDomain("example.com", "example2.com")

        allowedDomainsRepository.deleteAll(listOf("example.com"))

        assertNull(allowedDomainsRepository.getDomain("example2.com"))
        assertNotNull(allowedDomainsRepository.getDomain("example.com"))
    }

    private fun givenAllowedDomain(vararg allowedDomain: String) {
        allowedDomain.forEach {
            allowedDomainsDao.insert(AllowedDomainEntity(domain = it))
        }
    }
}
