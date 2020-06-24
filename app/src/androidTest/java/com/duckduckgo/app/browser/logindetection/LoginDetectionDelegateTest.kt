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
import com.duckduckgo.app.browser.logindetection.LoginDetectionDelegate.Event
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

    private val loginObserver = mock<Observer<LoginDetectionDelegate.LoginDetected>>()
    private val loginEventCaptor = argumentCaptor<LoginDetectionDelegate.LoginDetected>()

    @Before
    fun setup() {
        loginDetectionDelegate.loginEventLiveData.observeForever(loginObserver)
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToNewPageThenLoginDetected() {
        loginDetectionDelegate.onEvent(Event.LoginAttempt("http://example.com/login"))

        loginDetectionDelegate.onEvent(Event.WebNavigationEvent(WebNavigationStateChange.NewPage(url = "http://example.com", title = "")))

        assertEvent<LoginDetectionDelegate.LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToSamePageThenLoginNotDetected() {
        loginDetectionDelegate.onEvent(Event.LoginAttempt("http://example.com/login"))

        loginDetectionDelegate.onEvent(Event.WebNavigationEvent(WebNavigationStateChange.NewPage(url = "http://example.com/login", title = "")))

        assertEventNotIssued<LoginDetectionDelegate.LoginDetected>()
    }

    @Test
    fun whenNotDetectedLoginAttemptAndForwardedToNewPageThenLoginNotDetected() {
        loginDetectionDelegate.onEvent(Event.WebNavigationEvent(WebNavigationStateChange.NewPage(url = "http://example.com", title = "")))

        assertEventNotIssued<LoginDetectionDelegate.LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToNewUrlThenLoginDetected() {
        loginDetectionDelegate.onEvent(Event.LoginAttempt("http://example.com/login"))

        loginDetectionDelegate.onEvent(Event.WebNavigationEvent(WebNavigationStateChange.UrlUpdated(url = "http://example.com")))

        assertEvent<LoginDetectionDelegate.LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToSameUrlThenLoginNotDetected() {
        loginDetectionDelegate.onEvent(Event.LoginAttempt("http://example.com/login"))

        loginDetectionDelegate.onEvent(Event.WebNavigationEvent(WebNavigationStateChange.UrlUpdated(url = "http://example.com/login")))

        assertEventNotIssued<LoginDetectionDelegate.LoginDetected>()
    }

    @Test
    fun whenNotDetectedLoginAttemptAndForwardedToNewUrlThenLoginNotDetected() {
        loginDetectionDelegate.onEvent(Event.WebNavigationEvent(WebNavigationStateChange.NewPage(url = "http://example.com", title = "")))

        assertEventNotIssued<LoginDetectionDelegate.LoginDetected>()
    }


    @Test
    fun whenLoginAttemptedAndNextPageFinishedThenLoadingNewPageDoesNotDetectLogin() {
        loginDetectionDelegate.onEvent(Event.LoginAttempt("http://example.com/login"))
        loginDetectionDelegate.onEvent(Event.PageFinished)

        loginDetectionDelegate.onEvent(Event.WebNavigationEvent(WebNavigationStateChange.NewPage(url = "http://another.example.com", title = "")))

        assertEventNotIssued<LoginDetectionDelegate.LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserNavigatesBackThenNewPageDoesNotDetectLogin() {
        loginDetectionDelegate.onEvent(Event.LoginAttempt("http://example.com/login"))
        loginDetectionDelegate.onEvent(Event.UserAction.NavigateBack)

        loginDetectionDelegate.onEvent(Event.WebNavigationEvent(WebNavigationStateChange.NewPage(url = "http://another.example.com", title = "")))

        assertEventNotIssued<LoginDetectionDelegate.LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserNavigatesForwardThenNewPageDoesNotDetectLogin() {
        loginDetectionDelegate.onEvent(Event.LoginAttempt("http://example.com/login"))
        loginDetectionDelegate.onEvent(Event.UserAction.NavigateForward)

        loginDetectionDelegate.onEvent(Event.WebNavigationEvent(WebNavigationStateChange.NewPage(url = "http://another.example.com", title = "")))

        assertEventNotIssued<LoginDetectionDelegate.LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserReloadsWebsiteThenNewPageDoesNotDetectLogin() {
        loginDetectionDelegate.onEvent(Event.LoginAttempt("http://example.com/login"))
        loginDetectionDelegate.onEvent(Event.UserAction.Refresh)

        loginDetectionDelegate.onEvent(Event.WebNavigationEvent(WebNavigationStateChange.NewPage(url = "http://another.example.com", title = "")))

        assertEventNotIssued<LoginDetectionDelegate.LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserSubmitsNewQueryThenNewPageDoesNotDetectLogin() {
        loginDetectionDelegate.onEvent(Event.LoginAttempt("http://example.com/login"))
        loginDetectionDelegate.onEvent(Event.UserAction.NewQuerySubmitted)

        loginDetectionDelegate.onEvent(Event.WebNavigationEvent(WebNavigationStateChange.NewPage(url = "http://another.example.com", title = "")))

        assertEventNotIssued<LoginDetectionDelegate.LoginDetected>()
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