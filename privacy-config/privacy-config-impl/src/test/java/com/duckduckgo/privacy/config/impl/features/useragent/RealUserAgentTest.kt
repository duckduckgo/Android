/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.privacy.config.impl.features.useragent

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.privacy.config.api.UserAgentException
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.useragent.UserAgentRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class RealUserAgentTest {
    private val mockUserAgentRepository: UserAgentRepository = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()
    lateinit var testee: RealUserAgent

    @Before
    fun before() {
        testee = RealUserAgent(mockUserAgentRepository, mockUnprotectedTemporary)
    }

    @Test
    fun whenIsADefaultExceptionAndDomainIsListedInTheExceptionsListThenReturnTrue() {
        givenThereAreExceptions()

        assertTrue(testee.isADefaultException("http://www.example.com"))
    }

    @Test
    fun whenIsADefaultExceptionWithSubdomainAndDomainIsListedInTheExceptionsListThenReturnTrue() {
        givenThereAreExceptions()

        assertTrue(testee.isADefaultException("http://test.example.com"))
    }

    @Test
    fun whenIsADefaultExceptionAndDomainIsNotListedInTheExceptionsListThenReturnFalse() {
        whenever(mockUserAgentRepository.defaultExceptions).thenReturn(CopyOnWriteArrayList())

        assertFalse(testee.isADefaultException("http://test.example.com"))
    }

    @Test
    fun whenIsADefaultExceptionAndDomainIsListedInTheUnprotectedTemporaryListThenReturnTrue() {
        val url = "http://example.com"
        whenever(mockUnprotectedTemporary.isAnException(url)).thenReturn(true)
        whenever(mockUserAgentRepository.defaultExceptions).thenReturn(CopyOnWriteArrayList())

        assertTrue(testee.isADefaultException(url))
    }

    @Test
    fun whenIsAnApplicationExceptionAndDomainIsListedInTheExceptionsListThenReturnTrue() {
        givenThereAreExceptions()

        assertTrue(testee.isAnApplicationException("http://www.example.com"))
    }

    @Test
    fun whenIsAnApplicationExceptionWithSubdomainAndDomainIsListedInTheExceptionsListThenReturnTrue() {
        givenThereAreExceptions()

        assertTrue(testee.isAnApplicationException("http://test.example.com"))
    }

    @Test
    fun whenIsAnApplicationExceptionAndDomainIsNotListedInTheExceptionsListThenReturnFalse() {
        whenever(mockUserAgentRepository.omitApplicationExceptions).thenReturn(CopyOnWriteArrayList())

        assertFalse(testee.isAnApplicationException("http://test.example.com"))
    }

    @Test
    fun whenIsAVersionExceptionAndDomainIsListedInTheExceptionsListThenReturnTrue() {
        givenThereAreExceptions()

        assertTrue(testee.isAVersionException("http://www.example.com"))
    }

    @Test
    fun whenIsAVersionExceptionWithSubdomainAndDomainIsListedInTheExceptionsListThenReturnTrue() {
        givenThereAreExceptions()

        assertTrue(testee.isAVersionException("http://test.example.com"))
    }

    @Test
    fun whenIsAVersionExceptionAndDomainIsNotListedInTheExceptionsListThenReturnFalse() {
        whenever(mockUserAgentRepository.omitVersionExceptions).thenReturn(CopyOnWriteArrayList())

        assertFalse(testee.isAVersionException("http://test.example.com"))
    }

    private fun givenThereAreExceptions() {
        val exceptions =
            CopyOnWriteArrayList<UserAgentException>().apply {
                add(UserAgentException("example.com", "my reason here"))
            }
        whenever(mockUserAgentRepository.defaultExceptions).thenReturn(exceptions)
        whenever(mockUserAgentRepository.omitApplicationExceptions).thenReturn(exceptions)
        whenever(mockUserAgentRepository.omitVersionExceptions).thenReturn(exceptions)
    }
}
