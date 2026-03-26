/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.cookies.impl.thirdpartycookienames

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.cookies.api.ThirdPartyCookieNames
import com.duckduckgo.cookies.store.CookiesRepository
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CopyOnWriteArrayList

class RealThirdPartyCookieNamesTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    lateinit var testee: ThirdPartyCookieNames

    private val cookiesRepository: CookiesRepository = mock()

    @Before
    fun before() {
        testee = RealThirdPartyCookieNames(cookiesRepository)
    }

    @Test
    fun whenHasExcludedCookieNameCalledAndContainsCookieNameThenReturnTrue() {
        whenever(cookiesRepository.cookieNames).thenReturn(CopyOnWriteArrayList(listOf("anotherCookieName", COOKIE_NAME)))
        assertTrue(testee.hasExcludedCookieName(COOKIE_NAME))
    }

    @Test
    fun whenHasExcludedCookieNameCalledAndDoesNotContainCookieNameThenReturnFalse() {
        whenever(cookiesRepository.cookieNames).thenReturn(CopyOnWriteArrayList(listOf("anotherCookieName")))
        assertFalse(testee.hasExcludedCookieName(COOKIE_NAME))
    }

    companion object {
        private const val COOKIE_NAME = "cookieName"
    }
}
