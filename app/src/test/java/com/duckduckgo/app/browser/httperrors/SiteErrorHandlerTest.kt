/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.httperrors

import com.duckduckgo.app.global.model.Site
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SiteErrorHandlerTest {

    @Test
    fun `handleError for current site, then assign immediately`() {
        val testee = HttpCodeSiteErrorHandlerImpl()
        val errorValue = -1
        val currentSiteUrl = "error.com"
        val site = Mockito.mock<Site>()
        whenever(site.url).thenReturn(currentSiteUrl)

        testee.handleError(site, urlWithError = currentSiteUrl, errorValue)

        verify(site).onHttpErrorDetected(errorValue)
    }

    @Test
    fun `handleError but current site null, then cache, and assign later, and clear`() {
        val testee = HttpCodeSiteErrorHandlerImpl()
        val errorValue = -1
        val previousSite = null
        val newSiteUrl = "error.com"
        val newSite = Mockito.mock<Site>()
        whenever(newSite.url).thenReturn(newSiteUrl)

        testee.handleError(previousSite, newSiteUrl, errorValue) // cache
        testee.assignErrorsAndClearCache(newSite) // assign previous cache
        testee.assignErrorsAndClearCache(newSite) // try assigning again but cache should be cleared already

        verify(newSite, times(1)).onHttpErrorDetected(errorValue)
    }

    @Test
    fun `handleError (multiple) but current site different, then cache, and assign later, and clear`() {
        val testee = HttpCodeSiteErrorHandlerImpl()
        val errorValue1 = -1
        val errorValue2 = 2
        val previousSiteUrl = "site.com"
        val previousSite = Mockito.mock<Site>()
        whenever(previousSite.url).thenReturn(previousSiteUrl)
        val newSiteUrl = "error.com"
        val newSite = Mockito.mock<Site>()
        whenever(newSite.url).thenReturn(newSiteUrl)

        testee.handleError(previousSite, newSiteUrl, errorValue1) // cache
        testee.handleError(previousSite, newSiteUrl, errorValue2) // cache
        testee.assignErrorsAndClearCache(newSite) // assign previous cache
        testee.assignErrorsAndClearCache(newSite) // try assigning again but cache should be cleared already

        verify(newSite, times(1)).onHttpErrorDetected(errorValue1)
        verify(newSite, times(1)).onHttpErrorDetected(errorValue2)
        verify(previousSite, never()).onHttpErrorDetected(errorValue1)
        verify(previousSite, never()).onHttpErrorDetected(errorValue2)
    }

    @Test
    fun `handleError - first cache, then assign immediately, then assign later and clear`() {
        val testee = StringSiteErrorHandlerImpl()
        val errorValue1 = "error1"
        val errorValue2 = "error2"
        val previousSiteUrl = "site.com"
        val previousSite = Mockito.mock<Site>()
        whenever(previousSite.url).thenReturn(previousSiteUrl)
        val newSiteUrl = "error.com"
        val newSite = Mockito.mock<Site>()
        whenever(newSite.url).thenReturn(newSiteUrl)

        testee.handleError(previousSite, previousSiteUrl, errorValue1) // assign immediately
        testee.handleError(previousSite, newSiteUrl, errorValue2) // cache
        testee.assignErrorsAndClearCache(newSite) // assign previous cache
        testee.assignErrorsAndClearCache(newSite) // try assigning again but cache should be cleared already

        verify(previousSite, times(1)).onErrorDetected(errorValue1)
        verify(newSite, never()).onErrorDetected(errorValue1)
        verify(newSite, times(1)).onErrorDetected(errorValue2)
        verify(previousSite, never()).onErrorDetected(errorValue2)
    }
}
