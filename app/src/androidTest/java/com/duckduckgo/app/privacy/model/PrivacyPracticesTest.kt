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

import com.duckduckgo.app.privacy.model.PrivacyPractices.Practices
import com.duckduckgo.app.privacy.model.PrivacyPractices.Summary.GOOD
import com.duckduckgo.app.privacy.store.TermsOfServiceStore
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations.initMocks

class PrivacyPracticesTest {

    @Mock
    lateinit var mockTermsStore: TermsOfServiceStore

    @Before
    fun before() {
        initMocks(this)
    }

    @Test
    fun whenUrlButNoParentEntityThenStillHasScore() = runBlocking {
        whenever(mockTermsStore.terms).thenReturn(
            listOf(
                TermsOfService("example.com", classification = "D")
            )
        )

        val testee = PrivacyPracticesImpl(mockTermsStore)

        assertEquals(10, testee.privacyPracticesFor("http://www.example.com").score)
    }

    @Ignore("Need to review if this test is still valid; it might have been relying on the cached implementation")
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

        val testee = PrivacyPracticesImpl(mockTermsStore)
        assertEquals(10, testee.privacyPracticesFor("http://www.sibling1.com").score)
    }

    @Test
    fun whenUrlHasMatchingEntityWithTermsThenPracticesAreReturned() = runBlocking {
        whenever(mockTermsStore.terms).thenReturn(listOf(TermsOfService("example.com", classification = "A")))

        val testee = PrivacyPracticesImpl(mockTermsStore)

        val expected = Practices(score = 0, summary = GOOD, goodReasons = emptyList(), badReasons = emptyList())
        assertEquals(expected, testee.privacyPracticesFor("http://www.example.com"))
    }

    @Test
    fun whenInitialisedWithEmptyTermsStoreAndEntityListThenReturnsUnknownForUrl() = runBlocking {
        val testee = PrivacyPracticesImpl(mockTermsStore)
        assertEquals(PrivacyPractices.UNKNOWN, testee.privacyPracticesFor("http://www.example.com"))
    }
}