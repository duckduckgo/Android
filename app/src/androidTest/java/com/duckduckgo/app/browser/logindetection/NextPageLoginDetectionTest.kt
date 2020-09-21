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

package com.duckduckgo.app.browser.logindetection

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.WebNavigationStateChange
import com.duckduckgo.app.browser.WebNavigationStateChange.NewPage
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.mock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

class NextPageLoginDetectionTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val loginDetector = NextPageLoginDetection()

    private val loginObserver = mock<Observer<LoginDetected>>()
    private val loginEventCaptor = argumentCaptor<LoginDetected>()

    @Before
    fun setup() {
        loginDetector.loginEventLiveData.observeForever(loginObserver)
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToNewPageThenLoginDetected() {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

        fullyLoadSite("http://example.com")
        Thread.sleep(3000)

        assertEvent<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToAuthFlowPageThenLoginNotIssuedDetected() {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

        fullyLoadSite("https://accounts.google.com/o/oauth2/v2/auth/identifier")
        Thread.sleep(2000)
        fullyLoadSite("https://accounts.google.com/signin/v2/challenge/pwd?client_id=28300235456")
        Thread.sleep(2000)
        fullyLoadSite("https://accounts.google.com/signin/v2/challenge/az")
        Thread.sleep(2000)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToAuthFlowPageAndFinallyToOriginalDomainThenLoginDetected() {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://trello.com"))

        fullyLoadSite("https://accounts.google.com/o/oauth2/v2/auth/identifier")
        Thread.sleep(2000)
        fullyLoadSite("https://accounts.google.com/signin/v2/challenge/pwd?client_id=28300235456")
        Thread.sleep(2000)
        fullyLoadSite("https://accounts.google.com/signin/v2/challenge/az")
        Thread.sleep(2000)
        fullyLoadSite("https://trello.com/boards")
        Thread.sleep(2000)

        assertEvent<LoginDetected> {
            assertEquals("trello.com", this.forwardedToDomain)
        }
    }

    @Test
    fun whenLoginAttemptedViaFacebookAndUserForwardedToAuthFlowPageAndFinallyToOriginalDomainThenLoginDetected() {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("https://m.facebook.com/login.php"))

        fullyLoadSite("https://m.facebook.com/v7.0/dialog/oauth")
        Thread.sleep(2000)
        fullyLoadSite("https://m.facebook.com/v7.0/dialog/oauth/read/")
        Thread.sleep(2000)
        fullyLoadSite("https://www.spotify.com/es/account")
        Thread.sleep(2000)

        assertEvent<LoginDetected> {
            assertEquals("www.spotify.com", this.forwardedToDomain)
        }
    }

    @Test
    fun whenLoginAttemptedViaSSOAndUserForwardedToAuthFlowPageAndFinallyToOriginalDomainThenLoginDetected() {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("https://sso.duckduckgo.com/module.php/core/loginuserpass.php"))

        fullyLoadSite("https://sso.duckduckgo.com/module.php/duosecurity/getduo.php")
        Thread.sleep(2000)
        fullyLoadSite("https://sso.duckduckgo.com/module.php/duosecurity/getduo.php")
        Thread.sleep(2000)
        fullyLoadSite("https://app.asana.com/?rr=723841")
        Thread.sleep(2000)

        assertEvent<LoginDetected> {
            assertEquals("app.asana.com", this.forwardedToDomain)
        }
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToMultipleNewPagesThenLoginDetectedForLatestOne() {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

        fullyLoadSite("http://example.com")
        fullyLoadSite("http://example2.com")
        fullyLoadSite("http://example3.com")
        Thread.sleep(3000)

        assertEvent<LoginDetected> {
            assertEquals("example3.com", this.forwardedToDomain)
        }
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToSamePageThenLoginNotDetected() {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

        fullyLoadSite("http://example.com/login")
        Thread.sleep(3000)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenNotDetectedLoginAttemptAndForwardedToNewPageThenLoginNotDetected() {
        fullyLoadSite("http://example.com")
        Thread.sleep(3000)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToNewUrlThenLoginDetected() {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

        loginDetector.onEvent(NavigationEvent.WebNavigationEvent(WebNavigationStateChange.UrlUpdated(url = "http://example.com")))
        loginDetector.onEvent(NavigationEvent.PageFinished)
        Thread.sleep(3000)

        assertEvent<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToSameUrlThenLoginNotDetected() {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

        loginDetector.onEvent(NavigationEvent.WebNavigationEvent(WebNavigationStateChange.UrlUpdated(url = "http://example.com/login")))
        loginDetector.onEvent(NavigationEvent.PageFinished)
        Thread.sleep(3000)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenNotDetectedLoginAttemptAndForwardedToNewUrlThenLoginNotDetected() {
        loginDetector.onEvent(NavigationEvent.WebNavigationEvent(WebNavigationStateChange.UrlUpdated(url = "http://example.com")))
        loginDetector.onEvent(NavigationEvent.PageFinished)
        Thread.sleep(3000)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndNextPageFinishedThenLoadingNewPageDoesNotDetectLogin() {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
        loginDetector.onEvent(NavigationEvent.PageFinished)

        loginDetector.onEvent(NavigationEvent.WebNavigationEvent(NewPage(url = "http://another.example.com", title = "")))
        loginDetector.onEvent(NavigationEvent.PageFinished)
        Thread.sleep(3000)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserNavigatesBackThenNewPageDoesNotDetectLogin() {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
        loginDetector.onEvent(NavigationEvent.UserAction.NavigateBack)

        fullyLoadSite("http://another.example.com")
        Thread.sleep(3000)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserNavigatesForwardThenNewPageDoesNotDetectLogin() {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
        loginDetector.onEvent(NavigationEvent.UserAction.NavigateForward)

        fullyLoadSite("http://another.example.com")
        Thread.sleep(3000)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserReloadsWebsiteThenNewPageDoesNotDetectLogin() {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
        loginDetector.onEvent(NavigationEvent.UserAction.Refresh)

        fullyLoadSite("http://another.example.com")
        Thread.sleep(3000)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserSubmitsNewQueryThenNewPageDoesNotDetectLogin() {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
        loginDetector.onEvent(NavigationEvent.UserAction.NewQuerySubmitted)

        fullyLoadSite("http://another.example.com")
        Thread.sleep(3000)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedHasInvalidURLThenNewPageDoesNotDetectLogin() {
        loginDetector.onEvent(NavigationEvent.LoginAttempt(""))

        fullyLoadSite("http://example.com")
        Thread.sleep(3000)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToInvalidNewPageThenLoginDetected() {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

        fullyLoadSite("")
        Thread.sleep(3000)

        assertEventNotIssued<LoginDetected>()
    }

    private inline fun <reified T> assertEvent(instanceAssertions: T.() -> Unit = {}) {
        verify(loginObserver, atLeastOnce()).onChanged(loginEventCaptor.capture())
        val issuedCommand = loginEventCaptor.allValues.find { it is T }
        assertNotNull(issuedCommand)
        (issuedCommand as T).apply { instanceAssertions() }
    }

    private inline fun <reified T> assertEventNotIssued() {
        val issuedCommand = loginEventCaptor.allValues.find { it is T }
        assertNull(issuedCommand)
    }

    private fun fullyLoadSite(url: String) {
        loginDetector.onEvent(NavigationEvent.WebNavigationEvent(NewPage(url = url, title = "")))
        loginDetector.onEvent(NavigationEvent.PageFinished)
    }
}
