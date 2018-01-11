/*
 * Copyright (c) 2017 DuckDuckGo
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

import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HttpsUpgradeDomainDaoTest {

    companion object {
        var exactMatchDomain = "bbc.co.uk"
        var otherDomain = "other.com"
        var wildcardDomain = "*.wordpress.com"
        var otherWildcardDomain = "*.google.com"
        var exampleWildcardDomain = "example.wordpress.com"
        var parentOfWildcardDomain = "wordpress.com"
    }

    private lateinit var db: AppDatabase
    private lateinit var dao: HttpsUpgradeDomainDao

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getContext(), AppDatabase::class.java).build()
        dao = db.httpsUpgradeDomainDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenExactMatchDomainAddedAndThenAllDeletedThenDoesNotContainExactMatchDomain() {
        dao.insertAll(HttpsUpgradeDomain(exactMatchDomain))
        dao.deleteAll()
        assertFalse(dao.contains(exactMatchDomain))
    }

    @Test
    fun whenWildcardDomainInsertedModelThenDoesNotContainParentOfWildcardDomain() {
        dao.insertAll(HttpsUpgradeDomain(wildcardDomain))
        assertFalse(dao.contains(parentOfWildcardDomain))
    }

    @Test
    fun whenOtherWildcardDomainInsertedThenModelDoesNotContainExampleWildcardDomain() {
        dao.insertAll(HttpsUpgradeDomain(otherWildcardDomain))
        assertFalse(dao.contains(exampleWildcardDomain))
    }

    @Test
    fun whenWildcardDomainInsertedThenModelDoesNotContainExactMatchDomain() {
        dao.insertAll(HttpsUpgradeDomain(wildcardDomain))
        assertFalse(dao.contains(exactMatchDomain))
    }

    @Test
    fun whenWildcardDomainInsertedThenModelContainsExampleWildcardDomain() {
        dao.insertAll(HttpsUpgradeDomain(wildcardDomain))
        assertTrue(dao.contains(exampleWildcardDomain))
    }

    @Test
    fun whenExactMatchDomainInsertedThenModelDoesNotContainOtherDomain() {
        dao.insertAll(HttpsUpgradeDomain(exactMatchDomain))
        assertFalse(dao.contains(otherDomain))
    }

    @Test
    fun whenExactMatchDomainIsInsertedThenModelContainsExactMatchDomain() {
        dao.insertAll(HttpsUpgradeDomain(exactMatchDomain))
        assertTrue(dao.contains(exactMatchDomain))
    }

    @Test
    fun whenModelIsEmptyThenModelDoesNotContainExactMatchDomain() {
        assertFalse(dao.contains(exactMatchDomain))
    }

    @Test
    fun whenModelIsEmprtyThenCountIsZero() {
        assertEquals(0, dao.count())
    }

    @Test
    fun whenModelContainsTwoItemsThenCountIsTwo() {
        dao.insertAll(HttpsUpgradeDomain(exactMatchDomain), HttpsUpgradeDomain(otherDomain))
        assertEquals(2, dao.count())
    }

}