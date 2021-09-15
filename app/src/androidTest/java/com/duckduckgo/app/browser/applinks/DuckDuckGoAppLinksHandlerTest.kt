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
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
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
    fun whenAppLinkHandledAndIsRedirectAndAppLinkNotOpenedInBrowserThenReturnFalseAndLaunchAppLink() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            testee.appLinkOpenedInBrowser = false
            assertFalse(testee.handleAppLink(isRedirect = true, isForMainFrame = true, urlString = "foo.com", launchAppLink = mockCallback))
            verify(mockCallback).invoke()
        }
    }

    @Test
    fun whenAppLinkHandledAndIsSameOrSubdomainThenReturnFalse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            testee.appLinkOpenedInBrowser = false
            assertFalse(testee.handleAppLink(isRedirect = false, isForMainFrame = true, urlString = "example.com", launchAppLink = mockCallback))
        }
    }

    @Test
    fun whenAppLinkHandledAndIsNotSameOrSubdomainThenReturnFalseAndLaunchAppLink() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            testee.appLinkOpenedInBrowser = false
            assertFalse(testee.handleAppLink(isRedirect = false, isForMainFrame = true, urlString = "foo.com", launchAppLink = mockCallback))
            verify(mockCallback).invoke()
        }
    }

    @Test
    fun whenAppLinkHandledAndIsNotRedirectAndAppLinkOpenedInBrowserThenReturnFalseAndLaunchAppLink() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            testee.appLinkOpenedInBrowser = true
            assertFalse(testee.handleAppLink(isRedirect = false, isForMainFrame = true, urlString = "foo.com", launchAppLink = mockCallback))
            verify(mockCallback).invoke()
        }
    }

    @Test
    fun whenAppLinkHandledAndIsRedirectAndAppLinkOpenedInBrowserThenReturnFalse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            testee.appLinkOpenedInBrowser = true
            assertFalse(testee.handleAppLink(isRedirect = true, isForMainFrame = true, urlString = "foo.com", launchAppLink = mockCallback))
            verifyZeroInteractions(mockCallback)
        }
    }

    @Test
    fun whenAppLinkHandledAndIsForMainFrameThenReturnFalseAndLaunchAppLink() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            testee.appLinkOpenedInBrowser = false
            assertFalse(testee.handleAppLink(isRedirect = false, isForMainFrame = true, urlString = "foo.com", launchAppLink = mockCallback))
            verify(mockCallback).invoke()
        }
    }

    @Test
    fun whenAppLinkHandledAndIsNotForMainFrameThenReturnFalse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            testee.appLinkOpenedInBrowser = false
            assertFalse(testee.handleAppLink(isRedirect = false, isForMainFrame = false, urlString = "foo.com", launchAppLink = mockCallback))
            verifyZeroInteractions(mockCallback)
        }
    }

    @Test
    fun whenAppLinkHandledOnApiLessThan24ThenReturnFalse() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            testee.appLinkOpenedInBrowser = true
            assertFalse(testee.handleAppLink(isRedirect = true, isForMainFrame = false, urlString = "foo.com", launchAppLink = mockCallback))
            verifyZeroInteractions(mockCallback)
        }
    }

    @Test
    fun whenNonHttpAppLinkHandledAndIsRedirectAndAppLinkOpenedInBrowserThenReturnTrue() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            testee.appLinkOpenedInBrowser = true
            assertTrue(testee.handleNonHttpAppLink(true, mockCallback))
            verifyZeroInteractions(mockCallback)
        }
    }

    @Test
    fun whenNonHttpAppLinkHandledAndIsRedirectAndAppLinkOpenedInBrowserThenReturnTrueAndLaunchNonHttpAppLink() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            testee.appLinkOpenedInBrowser = false
            assertTrue(testee.handleNonHttpAppLink(true, mockCallback))
            verify(mockCallback).invoke()
        }
    }

    @Test
    fun whenNonHttpAppLinkHandledAndIsNotRedirectAndAppLinkOpenedInBrowserThenReturnTrueAndLaunchNonHttpAppLink() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            testee.appLinkOpenedInBrowser = true
            assertTrue(testee.handleNonHttpAppLink(false, mockCallback))
            verify(mockCallback).invoke()
        }
    }

    @Test
    fun whenNonHttpAppLinkHandledAndIsNotRedirectAndAppLinkNotOpenedInBrowserThenReturnTrueAndLaunchNonHttpAppLink() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            testee.appLinkOpenedInBrowser = false
            assertTrue(testee.handleNonHttpAppLink(false, mockCallback))
            verify(mockCallback).invoke()
        }
    }

    @Test
    fun whenNonHttpAppLinkHandledOnApiLessThan24ThenReturnTrueAndLaunchNonHttpAppLink() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            testee.appLinkOpenedInBrowser = true
            assertTrue(testee.handleNonHttpAppLink(true, mockCallback))
            verify(mockCallback).invoke()
        }
    }

    @Test
    fun whenEnterBrowserStateCalledThenSetAppLinkOpenedInBrowserToTrue() {
        assertFalse(testee.appLinkOpenedInBrowser)
        testee.enterBrowserState("foo.com")
        assertTrue(testee.appLinkOpenedInBrowser)
    }

    @Test
    fun whenResetCalledThenResetAppLinkState() {
        testee.appLinkOpenedInBrowser = true
        testee.reset()
        assertFalse(testee.appLinkOpenedInBrowser)
    }

    @Test
    fun whenPreviousUrlUpdatedThenResetAppLinkState() {
        testee.appLinkOpenedInBrowser = true
        testee.updatePreviousUrl("foo.com")
        assertFalse(testee.appLinkOpenedInBrowser)
    }
}
