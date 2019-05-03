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

package com.duckduckgo.app.privacy.model

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.entities.EntityMapping
import com.duckduckgo.app.entities.db.EntityListDao
import com.duckduckgo.app.entities.db.EntityListEntity
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.privacy.model.PrivacyPractices.Practices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.GOOD
import com.duckduckgo.app.privacy.store.TermsOfServiceStore
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations.initMocks

class PrivacyPracticesTest {

    @Mock
    lateinit var mockTermsStore: TermsOfServiceStore

    private lateinit var entityDao: EntityListDao

    private lateinit var entityMapping: EntityMapping

    private lateinit var testee: PrivacyPracticesImpl

    @Before
    fun before() {
        initMocks(this)

        entityDao = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .build()
            .networkEntityDao()

        entityMapping = EntityMapping(entityDao)

        testee = PrivacyPracticesImpl(mockTermsStore, entityMapping)
    }

    @Test
    fun whenUrlButNoParentEntityThenStillHasScore() = runBlocking {
        whenever(mockTermsStore.terms).thenReturn(
            listOf(
                TermsOfService("example.com", classification = "D")
            )
        )

        assertEquals(10, testee.privacyPracticesFor("http://www.example.com").score)
    }

    @Test
    fun whenUrlHasParentEntityThenItsScoreIsWorstInNetwork() = runBlocking {
        whenever(mockTermsStore.terms).thenReturn(
            listOf(
                TermsOfService("sibling1.com", classification = "A"),
                TermsOfService("sibling2.com", classification = "B"),
                TermsOfService("sibling3.com", classification = "C"),
                TermsOfService("sibling4.com", classification = "D")
            )
        )

        entityDao.insertAll(
            listOf(
                EntityListEntity("sibling1.com", "Network"),
                EntityListEntity("sibling2.com", "Network"),
                EntityListEntity("sibling3.com", "Network"),
                EntityListEntity("sibling4.com", "Network")
            )
        )

        testee.loadData()

        assertEquals(10, testee.privacyPracticesFor("http://www.sibling1.com").score)
    }

    @Test
    fun whenUrlHasMatchingEntityWithTermsThenPracticesAreReturned() = runBlocking {
        whenever(mockTermsStore.terms).thenReturn(listOf(TermsOfService("example.com", classification = "A")))
        val expected = Practices(score = 0, summary = GOOD, goodReasons = emptyList(), badReasons = emptyList())
        assertEquals(expected, testee.privacyPracticesFor("http://www.example.com"))
    }

    @Test
    fun whenInitialisedWithEmptyTermsStoreAndEntityListThenReturnsUnknownForUrl() = runBlocking {
        assertEquals(PrivacyPractices.UNKNOWN, testee.privacyPracticesFor("http://www.example.com"))
    }
}