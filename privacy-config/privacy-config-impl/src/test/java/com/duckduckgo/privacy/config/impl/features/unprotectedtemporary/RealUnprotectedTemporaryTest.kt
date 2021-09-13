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

package com.duckduckgo.privacy.config.impl.features.unprotectedtemporary

import com.duckduckgo.privacy.config.store.UnprotectedTemporaryEntity
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.UnprotectedTemporaryRepository
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
class RealUnprotectedTemporaryTest {
    private val mockUnprotectedTemporaryRepository: UnprotectedTemporaryRepository = mock()
    lateinit var testee: RealUnprotectedTemporary

    @Before
    fun before() {
        testee = RealUnprotectedTemporary(mockUnprotectedTemporaryRepository)
    }

    @Test
    fun whenIsAnExceptionAndDomainIsListedInTheExceptionsListThenReturnTrue() {
        givenThereAreExceptions()

        assertTrue(testee.isAnException("http://www.example.com"))
    }

    @Test
    fun whenIsAnExceptionWithSubdomainAndDomainIsListedInTheExceptionsListThenReturnTrue() {
        givenThereAreExceptions()

        assertTrue(testee.isAnException("http://test.example.com"))
    }

    @Test
    fun whenIsAnExceptionAndDomainIsNotListedInTheExceptionsListThenReturnFalse() {
        val exceptions = CopyOnWriteArrayList<UnprotectedTemporaryEntity>()
        whenever(mockUnprotectedTemporaryRepository.exceptions).thenReturn(exceptions)

        assertFalse(testee.isAnException("http://test.example.com"))
    }

    private fun givenThereAreExceptions() {
        val exceptions = CopyOnWriteArrayList<UnprotectedTemporaryEntity>()
        exceptions.add(UnprotectedTemporaryEntity("example.com", "my reason here"))

        whenever(mockUnprotectedTemporaryRepository.exceptions).thenReturn(exceptions)
    }
}
