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

package com.duckduckgo.app.httpsupgrade.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.httpsupgrade.model.HttpsWhitelistedDomain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HttpsWhitelistDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: HttpsWhitelistDao

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        dao = db.httpsWhitelistedDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenModelIsEmptyThenCountIsZero() {
        assertEquals(0, dao.count())
    }

    @Test
    fun whenModelIsEmptyThenContainsDomainIsFalse() {
        assertFalse(dao.contains(domain))
    }

    @Test
    fun whenDomainInsertedThenContainsDomainIsTrue() {
        dao.insertAll(listOf(HttpsWhitelistedDomain(domain)))
        assertTrue(dao.contains(domain))
    }

    @Test
    fun whenDomainInsertedThenCountIsOne() {
        dao.insertAll(listOf(HttpsWhitelistedDomain(domain)))
        assertEquals(1, dao.count())
    }

    @Test
    fun whenSecondUniqueDomainInsertedThenCountIsTwo() {
        dao.insertAll(listOf(HttpsWhitelistedDomain(domain)))
        dao.insertAll(listOf(HttpsWhitelistedDomain(anotherDomain)))
        assertEquals(2, dao.count())
    }

    @Test
    fun whenSecondDuplicateDomainInsertedThenCountIsOne() {
        dao.insertAll(listOf(HttpsWhitelistedDomain(domain)))
        dao.insertAll(listOf(HttpsWhitelistedDomain(domain)))
        assertEquals(1, dao.count())
    }

    @Test
    fun whenAllUpdatedThenPreviousValuesAreReplaced() {
        dao.insertAll(listOf(HttpsWhitelistedDomain(domain)))
        dao.updateAll(listOf(HttpsWhitelistedDomain(anotherDomain)))
        assertEquals(1, dao.count())
        assertTrue(dao.contains(anotherDomain))
    }

    @Test
    fun whenAllDeletedThenContainsDomainIsFalse() {
        dao.insertAll(listOf(HttpsWhitelistedDomain(domain)))
        dao.deleteAll()
        assertFalse(dao.contains(domain))
    }

    companion object {
        var domain = "domain.com"
        var anotherDomain = "another.com"
    }
}
