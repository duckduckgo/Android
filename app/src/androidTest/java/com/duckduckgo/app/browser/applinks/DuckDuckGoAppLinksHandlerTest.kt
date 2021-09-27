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

package com.duckduckgo.app.browser.applinks

import android.os.Build
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import junit.framework.TestCase.*
import org.junit.Before
import org.junit.Test

class DuckDuckGoAppLinksHandlerTest {

    private lateinit var testee: DuckDuckGoAppLinksHandler

    private var mockCallback: () -> Unit = mock()

    @Before
    fun setup() {
        testee = DuckDuckGoAppLinksHandler()
        testee.previousUrl = "example.com"
    }

    @Test
    fun whenAppLinkHandledAndIsSameOrSubdomainThenReturnFalse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            assertFalse(testee.handleAppLink(isForMainFrame = true, urlString = "example.com", launchAppLink = mockCallback, shouldOverride = true, appLinksEnabled = true))
        }
    }

    @Test
    fun whenAppLinkHandledAndIsNotSameOrSubdomainThenReturnFalseAndLaunchAppLink() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            assertFalse(testee.handleAppLink(isForMainFrame = true, urlString = "foo.com", launchAppLink = mockCallback, shouldOverride = false, appLinksEnabled = true))
            verify(mockCallback).invoke()
        }
    }

    @Test
    fun whenAppLinkHandledAndIsNotForMainFrameThenReturnFalse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            assertFalse(testee.handleAppLink(isForMainFrame = false, urlString = "foo.com", launchAppLink = mockCallback, shouldOverride = true, appLinksEnabled = true))
            verifyZeroInteractions(mockCallback)
        }
    }

    @Test
    fun whenAppLinkHandledOnApiLessThan24ThenReturnFalse() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            assertFalse(testee.handleAppLink(isForMainFrame = false, urlString = "foo.com", launchAppLink = mockCallback, shouldOverride = true, appLinksEnabled = true))
            verifyZeroInteractions(mockCallback)
        }
    }

    @Test
    fun whenPreviousUrlUpdatedThenUpdatePreviousUrl() {
        testee.updatePreviousUrl("foo.com")
        assertEquals("foo.com", testee.previousUrl)
    }
}
