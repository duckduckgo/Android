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

import com.duckduckgo.app.entities.EntityMapping
import com.duckduckgo.app.entities.db.EntityListEntity
import com.duckduckgo.app.privacy.model.PrivacyPractices.Practices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.GOOD
import com.duckduckgo.app.privacy.store.TermsOfServiceStore
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations.initMocks

class PrivacyPracticesTest {

    private val entityMapping = EntityMapping()

    @Mock
    lateinit var mockTermsStore: TermsOfServiceStore

    @Before
    fun before() {
        initMocks(this)
    }

    @Test
    fun whenUrlButNoParentEntityThenStillHasScore() {
        whenever(mockTermsStore.terms).thenReturn(
            listOf(
                TermsOfService("example.com", classification = "D")
            )
        )

        val testee = PrivacyPracticesImpl(mockTermsStore, entityMapping)

        assertEquals(10, testee.privacyPracticesFor("http://www.example.com").score)

    }

    @Test
    fun whenUrlHasParentEntityThenItsScoreIsWorstInNetwork() {
        whenever(mockTermsStore.terms).thenReturn(
            listOf(
                TermsOfService("sibling1.com", classification = "A"),
                TermsOfService("sibling2.com", classification = "B"),
                TermsOfService("sibling3.com", classification = "C"),
                TermsOfService("sibling4.com", classification = "D")
            )
        )

        entityMapping.updateEntities(
            listOf(
                EntityListEntity("sibling1.com", "Network"),
                EntityListEntity("sibling2.com", "Network"),
                EntityListEntity("sibling3.com", "Network"),
                EntityListEntity("sibling4.com", "Network")
            )
        )

        val testee = PrivacyPracticesImpl(mockTermsStore, entityMapping)

        assertEquals(10, testee.privacyPracticesFor("http://www.sibling1.com").score)
    }

    @Test
    fun whenUrlHasMatchingEntityWithTermsThenPracticesAreReturned() {
        whenever(mockTermsStore.terms).thenReturn(listOf(TermsOfService("example.com", classification = "A")))

        entityMapping.updateEntities(listOf(EntityListEntity("example.com", "Network")))

        val testee = PrivacyPracticesImpl(mockTermsStore, entityMapping)

        val expected = Practices(score = 0, summary = GOOD, goodReasons = emptyList(), badReasons = emptyList())
        assertEquals(expected, testee.privacyPracticesFor("http://www.example.com"))
    }

    @Test
    fun whenInitialisedWithEmptyTermsStoreAndEntityListThenReturnsUnknownForUrl() {
        val testee = PrivacyPracticesImpl(mockTermsStore, entityMapping)
        assertEquals(PrivacyPractices.UNKNOWN, testee.privacyPracticesFor("http://www.example.com"))
    }

}