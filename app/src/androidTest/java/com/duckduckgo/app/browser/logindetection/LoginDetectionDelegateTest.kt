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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

class LoginDetectionDelegateTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val loginDetectionDelegate = LoginDetectionDelegate()

    private val loginObserver = mock<Observer<LoginDetected>>()
    private val loginEventCaptor = argumentCaptor<LoginDetected>()

    @Before
    fun setup() {
        loginDetectionDelegate.loginEventLiveData.observeForever(loginObserver)
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToNewPageThenLoginDetected() {
        loginDetectionDelegate.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

        loginDetectionDelegate.onEvent(NavigationEvent.WebNavigationEvent(NewPage(url = "http://example.com", title = "")))

        assertEvent<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToSamePageThenLoginNotDetected() {
        loginDetectionDelegate.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

        loginDetectionDelegate.onEvent(NavigationEvent.WebNavigationEvent(NewPage(url = "http://example.com/login", title = "")))

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenNotDetectedLoginAttemptAndForwardedToNewPageThenLoginNotDetected() {
        loginDetectionDelegate.onEvent(NavigationEvent.WebNavigationEvent(NewPage(url = "http://example.com", title = "")))

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToNewUrlThenLoginDetected() {
        loginDetectionDelegate.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

        loginDetectionDelegate.onEvent(NavigationEvent.WebNavigationEvent(WebNavigationStateChange.UrlUpdated(url = "http://example.com")))

        assertEvent<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToSameUrlThenLoginNotDetected() {
        loginDetectionDelegate.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

        loginDetectionDelegate.onEvent(NavigationEvent.WebNavigationEvent(WebNavigationStateChange.UrlUpdated(url = "http://example.com/login")))

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenNotDetectedLoginAttemptAndForwardedToNewUrlThenLoginNotDetected() {
        loginDetectionDelegate.onEvent(NavigationEvent.WebNavigationEvent(NewPage(url = "http://example.com", title = "")))

        assertEventNotIssued<LoginDetected>()
    }


    @Test
    fun whenLoginAttemptedAndNextPageFinishedThenLoadingNewPageDoesNotDetectLogin() {
        loginDetectionDelegate.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
        loginDetectionDelegate.onEvent(NavigationEvent.PageFinished)

        loginDetectionDelegate.onEvent(NavigationEvent.WebNavigationEvent(NewPage(url = "http://another.example.com", title = "")))

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserNavigatesBackThenNewPageDoesNotDetectLogin() {
        loginDetectionDelegate.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
        loginDetectionDelegate.onEvent(NavigationEvent.UserAction.NavigateBack)

        loginDetectionDelegate.onEvent(NavigationEvent.WebNavigationEvent(NewPage(url = "http://another.example.com", title = "")))

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserNavigatesForwardThenNewPageDoesNotDetectLogin() {
        loginDetectionDelegate.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
        loginDetectionDelegate.onEvent(NavigationEvent.UserAction.NavigateForward)

        loginDetectionDelegate.onEvent(NavigationEvent.WebNavigationEvent(NewPage(url = "http://another.example.com", title = "")))

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserReloadsWebsiteThenNewPageDoesNotDetectLogin() {
        loginDetectionDelegate.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
        loginDetectionDelegate.onEvent(NavigationEvent.UserAction.Refresh)

        loginDetectionDelegate.onEvent(NavigationEvent.WebNavigationEvent(NewPage(url = "http://another.example.com", title = "")))

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserSubmitsNewQueryThenNewPageDoesNotDetectLogin() {
        loginDetectionDelegate.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
        loginDetectionDelegate.onEvent(NavigationEvent.UserAction.NewQuerySubmitted)

        loginDetectionDelegate.onEvent(NavigationEvent.WebNavigationEvent(NewPage(url = "http://another.example.com", title = "")))

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

}