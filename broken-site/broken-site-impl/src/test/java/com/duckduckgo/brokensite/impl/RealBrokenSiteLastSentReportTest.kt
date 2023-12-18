/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.brokensite.impl

import com.duckduckgo.brokensite.api.BrokenSiteLastSentReport
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class RealBrokenSiteLastSentReportTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockBrokenSiteReportRepository: BrokenSiteReportRepository = mock()
    private lateinit var testee: BrokenSiteLastSentReport

    @Before
    fun before() {
        testee = RealBrokenSiteLastSentReport(mockBrokenSiteReportRepository)
    }

    @Test
    fun whenGetLastSentDayCalledWithHostnameThenGetLastSentDayFromRepositoryIsCalled() = runTest {
        val hostname = "www.example.com"

        testee.getLastSentDay(hostname)

        verify(mockBrokenSiteReportRepository).getLastSentDay(hostname)
    }

    @Test
    fun whenSetLastSentDayCalledWithHostnameThenSetLastSentDayFromRepositoryIsCalled() = runTest {
        val hostname = "www.example.com"

        testee.setLastSentDay(hostname)

        verify(mockBrokenSiteReportRepository).setLastSentDay(hostname)
    }
}
