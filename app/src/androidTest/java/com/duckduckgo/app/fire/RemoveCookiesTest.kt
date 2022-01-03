/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.fire

import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@ExperimentalCoroutinesApi
class RemoveCookiesTest {

    private val selectiveCookieRemover = mock<CookieRemover>()
    private val cookieManagerRemover = mock<CookieRemover>()
    private val removeCookies = RemoveCookies(cookieManagerRemover, selectiveCookieRemover)

    @Test
    fun whenSelectiveCookieRemoverSucceedsThenNoMoreInteractions() = runTest {
        selectiveCookieRemover.succeeds()

        removeCookies.removeCookies()

        verifyNoInteractions(cookieManagerRemover)
    }

    @Test
    fun whenSelectiveCookieRemoverFailsThenFallbackToCookieManagerRemover() = runTest {
        selectiveCookieRemover.fails()

        removeCookies.removeCookies()

        verify(cookieManagerRemover).removeCookies()
    }

    private suspend fun CookieRemover.succeeds() {
        whenever(this.removeCookies()).thenReturn(true)
    }

    private suspend fun CookieRemover.fails() {
        whenever(this.removeCookies()).thenReturn(false)
    }
}
