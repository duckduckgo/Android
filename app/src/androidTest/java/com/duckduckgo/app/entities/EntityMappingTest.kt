/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.entities

import com.duckduckgo.app.entities.db.EntityListDao
import com.duckduckgo.app.entities.db.EntityListEntity
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class EntityMappingTest {

    private val mockDao: EntityListDao = mock()
    private lateinit var testee: EntityMapping

    @Before
    fun setup() {
        testee = EntityMapping(mockDao)
    }

    @Test
    fun whenUrlContainsOneUnmatchedDomainThenNoValueIsReturned() {
        val url = "a.com"
        whenever(mockDao.get(url)).thenReturn(null)
        val entity = testee.entityForUrl("https://$url")
        assertNull(entity)
        verify(mockDao).get("a.com")
    }

    @Test
    fun whenUrlContainsOneMatchingDomainThenValueIsReturned() {
        val url = "a.com"
        whenever(mockDao.get(url)).thenReturn(anEntity())
        val entity = testee.entityForUrl("https://$url")
        assertNotNull(entity)
    }

    @Test
    fun whenUrlContainsOneMatchingDomainThenDomainIsSearchedFor() {
        val url = "a.com"
        testee.entityForUrl("https://$url")
        verify(mockDao).get("a.com")
        verify(mockDao, never()).get("com")
    }

    @Test
    fun whenUrlContainsOneUnmatchedSubDomainAndOneMatchingDomainThenValueIsReturned() {
        val url = "a.b.com"
        val domains = listOf("a.b.com", "b.com")
        whenever(mockDao.get(domains)).doReturn(anEntity())
        val entity = testee.entityForUrl("https://$url")
        assertNotNull(entity)
        verify(mockDao).get(domains)
    }

    @Test
    fun whenUrlContainsOneMultipartTldThenTldIsSearchedForInDb() {
        val url = "a.co.uk"
        val domains = listOf("a.co.uk", "co.uk")
        testee.entityForUrl("https://$url")
        verify(mockDao).get(domains)
    }

    private fun anEntity() = EntityListEntity("", "")
}