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

package com.duckduckgo.privacy.config.impl.features.autofill

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.privacy.config.api.AutofillException
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.autofill.AutofillRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class RealAutofillTest {
    private val mockAutofillRepository: AutofillRepository = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()
    lateinit var testee: RealAutofill

    @Before
    fun before() {
        testee = RealAutofill(mockAutofillRepository, mockUnprotectedTemporary)
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
        whenever(mockAutofillRepository.exceptions).thenReturn(CopyOnWriteArrayList())

        assertFalse(testee.isAnException("http://test.example.com"))
    }

    @Test
    fun whenIsAnExceptionAndDomainIsListedInTheUnprotectedTemporaryListThenReturnTrue() {
        val url = "http://example.com"
        whenever(mockUnprotectedTemporary.isAnException(url)).thenReturn(true)
        whenever(mockAutofillRepository.exceptions).thenReturn(CopyOnWriteArrayList())

        assertTrue(testee.isAnException(url))
    }

    private fun givenThereAreExceptions() {
        val exceptions =
            CopyOnWriteArrayList<AutofillException>().apply {
                add(AutofillException("example.com", "my reason here"))
            }
        whenever(mockAutofillRepository.exceptions).thenReturn(exceptions)
    }
}
