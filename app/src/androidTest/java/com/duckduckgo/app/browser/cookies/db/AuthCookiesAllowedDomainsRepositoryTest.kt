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
class AuthCookiesAllowedDomainsRepositoryTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var authCookiesAllowedDomainsDao: AuthCookiesAllowedDomainsDao
    private lateinit var authCookiesAllowedDomainsRepository: AuthCookiesAllowedDomainsRepository

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        authCookiesAllowedDomainsDao = db.authCookiesAllowedDomainsDao()
        authCookiesAllowedDomainsRepository = AuthCookiesAllowedDomainsRepository(authCookiesAllowedDomainsDao, coroutineRule.testDispatcherProvider)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenAddDomainIfIsEmptyThenReturnNull() = coroutineRule.runBlocking {
        assertNull(authCookiesAllowedDomainsRepository.addDomain(""))
    }

    @Test
    fun whenAddDomainAndDomainNotValidThenReturnNull() = coroutineRule.runBlocking {
        assertNull(authCookiesAllowedDomainsRepository.addDomain("https://example.com"))
    }

    @Test
    fun whenAddValidDomainThenReturnNonNull() = coroutineRule.runBlocking {
        assertNotNull(authCookiesAllowedDomainsRepository.addDomain("example.com"))
    }

    @Test
    fun whenGetDomainIfDomainExistsThenReturnAllowedDomainEntity() = coroutineRule.runBlocking {
        givenAuthCookieAllowedDomain("example.com")

        val authCookieAllowedDomainEntity = authCookiesAllowedDomainsRepository.getDomain("example.com")
        assertEquals("example.com", authCookieAllowedDomainEntity?.domain)
    }

    @Test
    fun whenGetDomainIfDomainDoesNotExistThenReturnNull() = coroutineRule.runBlocking {
        val authCookieAllowedDomainEntity = authCookiesAllowedDomainsRepository.getDomain("example.com")
        assertNull(authCookieAllowedDomainEntity)
    }

    @Test
    fun whenRemoveDomainThenDomainDeletedFromDatabase() = coroutineRule.runBlocking {
        givenAuthCookieAllowedDomain("example.com")
        val authCookieAllowedDomainEntity = authCookiesAllowedDomainsRepository.getDomain("example.com")

        authCookiesAllowedDomainsRepository.removeDomain(authCookieAllowedDomainEntity!!)

        val deletedEntity = authCookiesAllowedDomainsRepository.getDomain("example.com")
        assertNull(deletedEntity)
    }

    @Test
    fun whenDeleteAllThenAllDomainsDeletedExceptFromTheExceptionList() = coroutineRule.runBlocking {
        givenAuthCookieAllowedDomain("example.com", "example2.com")

        authCookiesAllowedDomainsRepository.deleteAll(listOf("example.com"))

        assertNull(authCookiesAllowedDomainsRepository.getDomain("example2.com"))
        assertNotNull(authCookiesAllowedDomainsRepository.getDomain("example.com"))
    }

    private fun givenAuthCookieAllowedDomain(vararg allowedDomain: String) {
        allowedDomain.forEach {
            authCookiesAllowedDomainsDao.insert(AuthCookieAllowedDomainEntity(domain = it))
        }
    }
}
