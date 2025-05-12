/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.malicioussiteprotection.impl.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.impl.models.Filter
import com.duckduckgo.malicioussiteprotection.impl.models.FilterSetWithRevision.PhishingFilterSetWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.HashPrefixesWithRevision.PhishingHashPrefixesWithRevision
import com.duckduckgo.malicioussiteprotection.impl.models.Type
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaliciousSiteDaoTest {

    private lateinit var database: MaliciousSitesDatabase
    private lateinit var dao: MaliciousSiteDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, MaliciousSitesDatabase::class.java).allowMainThreadQueries().build()
        dao = database.maliciousSiteDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testUpdateFiltersWithReplaceDeletesOldEntries() = runTest {
        dao.insertFilters(setOf(FilterEntity(hash = "hash1", regex = "regex1", type = Feed.PHISHING.name)))
        val filterSet = PhishingFilterSetWithRevision(
            revision = 1,
            replace = true,
            delete = setOf(),
            insert = setOf(Filter(hash = "hash2", regex = "regex2")),
        )

        dao.updateFilters(filterSet)

        assertNotNull(dao.getFilter("hash2"))
        assertNull(dao.getFilter("hash1"))
    }

    @Test
    fun testUpdateFiltersWithoutReplaceDoesNotDeleteOldEntries() = runTest {
        dao.insertFilters(setOf(FilterEntity(hash = "hash1", regex = "regex1", type = Feed.PHISHING.name)))
        val filterSet = PhishingFilterSetWithRevision(
            revision = 1,
            replace = false,
            delete = setOf(),
            insert = setOf(Filter(hash = "hash2", regex = "regex2")),
        )

        dao.updateFilters(filterSet)

        assertNotNull(dao.getFilter("hash2"))
        assertNotNull(dao.getFilter("hash1"))
    }

    @Test
    fun testUpdateHashPrefixesWithReplaceDeletesOldEntries() = runTest {
        dao.insertHashPrefixes(listOf(HashPrefixEntity(hashPrefix = "prefix1", type = Feed.PHISHING.name)))
        val hashPrefixes = PhishingHashPrefixesWithRevision(
            revision = 1,
            replace = true,
            delete = setOf(),
            insert = setOf("prefix2"),
        )

        dao.updateHashPrefixes(hashPrefixes)

        assertNotNull(dao.getHashPrefix("prefix2"))
        assertNull(dao.getHashPrefix("prefix1"))
    }

    @Test
    fun testUpdateHashPrefixesWithoutReplaceDoesNotDeleteOldEntries() = runTest {
        dao.insertHashPrefixes(listOf(HashPrefixEntity(hashPrefix = "prefix1", type = Feed.PHISHING.name)))
        val hashPrefixes = PhishingHashPrefixesWithRevision(
            revision = 1,
            replace = false,
            delete = setOf(),
            insert = setOf("prefix2"),
        )

        dao.updateHashPrefixes(hashPrefixes)

        assertNotNull(dao.getHashPrefix("prefix2"))
        assertNotNull(dao.getHashPrefix("prefix1"))
    }

    @Test
    fun testFiltersNotUpdatedIfRevisionIsLowerThanCurrent() = runTest {
        dao.insertFilters(setOf(FilterEntity(hash = "hash1", regex = "regex1", type = Feed.PHISHING.name)))
        dao.insertRevision(RevisionEntity(feed = Feed.PHISHING.name, type = Type.FILTER_SET.name, revision = 2))
        val filterSet = PhishingFilterSetWithRevision(
            revision = 1,
            replace = true,
            delete = setOf(),
            insert = setOf(Filter(hash = "hash2", regex = "regex2")),
        )

        dao.updateFilters(filterSet)

        assertNotNull(dao.getFilter("hash1"))
        assertNull(dao.getFilter("hash2"))
    }

    @Test
    fun testFiltersUpdatedIfRevisionIsHigherThanCurrent() = runTest {
        dao.insertFilters(setOf(FilterEntity(hash = "hash1", regex = "regex1", type = Feed.PHISHING.name)))
        dao.insertRevision(RevisionEntity(feed = Feed.PHISHING.name, type = Type.FILTER_SET.name, revision = 1))
        val filterSet = PhishingFilterSetWithRevision(
            revision = 2,
            replace = true,
            delete = setOf(),
            insert = setOf(Filter(hash = "hash2", regex = "regex2")),
        )

        dao.updateFilters(filterSet)

        assertNull(dao.getFilter("hash1"))
        assertNotNull(dao.getFilter("hash2"))
    }

    @Test
    fun testHashPrefixesNotUpdatedIfRevisionIsLowerThanCurrent() = runTest {
        dao.insertHashPrefixes(listOf(HashPrefixEntity(hashPrefix = "prefix1", type = Feed.PHISHING.name)))
        dao.insertRevision(RevisionEntity(feed = Feed.PHISHING.name, type = Type.HASH_PREFIXES.name, revision = 2))
        val hashPrefixes = PhishingHashPrefixesWithRevision(
            revision = 1,
            replace = true,
            delete = setOf(),
            insert = setOf("prefix2"),
        )

        dao.updateHashPrefixes(hashPrefixes)

        assertNotNull(dao.getHashPrefix("prefix1"))
        assertNull(dao.getHashPrefix("prefix2"))
    }

    @Test
    fun testHashPrefixesUpdatedIfRevisionIsHigherThanCurrent() = runTest {
        dao.insertHashPrefixes(listOf(HashPrefixEntity(hashPrefix = "prefix1", type = Feed.PHISHING.name)))
        dao.insertRevision(RevisionEntity(feed = Feed.PHISHING.name, type = Type.HASH_PREFIXES.name, revision = 1))
        val hashPrefixes = PhishingHashPrefixesWithRevision(
            revision = 2,
            replace = true,
            delete = setOf(),
            insert = setOf("prefix2"),
        )

        dao.updateHashPrefixes(hashPrefixes)

        assertNull(dao.getHashPrefix("prefix1"))
        assertNotNull(dao.getHashPrefix("prefix2"))
    }
}
