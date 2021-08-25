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

package com.duckduckgo.privacy.config.impl.features.contentblocking

import com.duckduckgo.privacy.config.api.ContentBlockingException
import com.duckduckgo.privacy.config.store.features.contentblocking.ContentBlockingRepository
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RealContentBlockingTest {

    lateinit var testee: RealContentBlocking

    private val mockContentBlockingRepository: ContentBlockingRepository = mock()

    @Before
    fun before() {
        testee = RealContentBlocking(mockContentBlockingRepository)
    }

    @Test
    fun whenIsAnExceptionAndDomainIsListedInTheExceptionsListThenReturnTrue() {
        whenever(mockContentBlockingRepository.exceptions).thenReturn(
            arrayListOf(
                ContentBlockingException("example.com", "my reason here")
            )
        )

        assertTrue(testee.isAnException("http://www.example.com"))
    }

    @Test
    fun whenIsAnExceptionWithSubdomainAndDomainIsListedInTheExceptionsListThenReturnTrue() {
        whenever(mockContentBlockingRepository.exceptions).thenReturn(
            arrayListOf(
                ContentBlockingException("example.com", "my reason here")
            )
        )

        assertTrue(testee.isAnException("http://test.example.com"))
    }

    @Test
    fun whenIsAnExceptionAndDomainIsNotListedInTheExceptionsListThenReturnFalse() {
        whenever(mockContentBlockingRepository.exceptions).thenReturn(arrayListOf())

        assertFalse(testee.isAnException("http://test.example.com"))
    }
}
