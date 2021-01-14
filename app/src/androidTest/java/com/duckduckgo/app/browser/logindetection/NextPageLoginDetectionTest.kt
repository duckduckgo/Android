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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.WebNavigationStateChange
import com.duckduckgo.app.browser.WebNavigationStateChange.NewPage
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class NextPageLoginDetectionTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

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
    fun whenLoginAttemptedAndUserForwardedToNewPageThenLoginDetected() = coroutineRule.runBlocking {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

        redirectTo("http://example.com")
        delay(LOGIN_DETECTOR_JOB_DELAY)

        assertEvent<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedInsideOAuthFlowThenLoginDetectedWhenUserForwardedToDifferentDomain() = coroutineRule.runBlocking {
        redirectTo("https://accounts.google.com/o/oauth2/v2/auth")
        delay(LOGIN_DETECTOR_JOB_DELAY)
        redirectTo("https://accounts.google.com/signin/v2/challenge/pwd")
        delay(LOGIN_DETECTOR_JOB_DELAY)
        loginDetector.onEvent(NavigationEvent.LoginAttempt("https://accounts.google.com/signin/v2/challenge"))
        redirectTo("https://accounts.google.com/signin/v2/challenge/az?client_id")
        delay(LOGIN_DETECTOR_JOB_DELAY)
        redirectTo("https://accounts.google.com/randomPath")
        delay(LOGIN_DETECTOR_JOB_DELAY)
        redirectTo("http://example.com")
        delay(LOGIN_DETECTOR_JOB_DELAY)

        assertEvent<LoginDetected> {
            assertEquals("example.com", this.forwardedToDomain)
        }
    }

    @Test
    fun whenLoginAttemptedInsideSSOFlowThenLoginDetectedWhenUserForwardedToDifferentDomain() = coroutineRule.runBlocking {
        fullyLoadSite("https://app.asana.com/-/login")
        delay(LOGIN_DETECTOR_JOB_DELAY)
        redirectTo("https://sso.host.com/saml2/idp/SSOService.php")
        delay(LOGIN_DETECTOR_JOB_DELAY)
        fullyLoadSite("https://sso.host.com/module.php/core/loginuserpass.php")
        delay(LOGIN_DETECTOR_JOB_DELAY)
        loginDetector.onEvent(NavigationEvent.LoginAttempt("https://sso.host.com/module.php/core/loginuserpass.php"))
        redirectTo("https://sso.host.com/module.php/duosecurity/getduo.php")
        delay(LOGIN_DETECTOR_JOB_DELAY)
        redirectTo("https://app.asana.com/")
        delay(LOGIN_DETECTOR_JOB_DELAY)

        assertEvent<LoginDetected> {
            assertEquals("app.asana.com", this.forwardedToDomain)
        }
    }

    @Test
    fun whenLoginAttemptedSkip2FAUrlsThenLoginDetectedForLatestOne() = coroutineRule.runBlocking {
        fullyLoadSite("https://accounts.google.com/ServiceLogin")
        delay(LOGIN_DETECTOR_JOB_DELAY)
        fullyLoadSite("https://accounts.google.com/signin/v2/challenge/pwd")
        delay(LOGIN_DETECTOR_JOB_DELAY)
        loginDetector.onEvent(NavigationEvent.LoginAttempt("https://accounts.google.com/signin/v2/challenge/pwd"))
        redirectTo("https://accounts.google.com/signin/v2/challenge/az")
        delay(LOGIN_DETECTOR_JOB_DELAY)
        redirectTo("https://mail.google.com/mail")
        delay(LOGIN_DETECTOR_JOB_DELAY)

        assertEvent<LoginDetected> {
            assertEquals("mail.google.com", this.forwardedToDomain)
        }
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToMultipleNewPagesThenLoginDetectedForLatestOne() = coroutineRule.runBlocking {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

        fullyLoadSite("http://example.com")
        fullyLoadSite("http://example2.com")
        fullyLoadSite("http://example3.com")
        delay(LOGIN_DETECTOR_JOB_DELAY)

        assertEvent<LoginDetected> {
            assertEquals("example3.com", this.forwardedToDomain)
        }
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToSamePageThenLoginNotDetected() = coroutineRule.runBlocking {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

        fullyLoadSite("http://example.com/login")
        delay(LOGIN_DETECTOR_JOB_DELAY)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenNotDetectedLoginAttemptAndForwardedToNewPageThenLoginNotDetected() = coroutineRule.runBlocking {
        fullyLoadSite("http://example.com")
        delay(LOGIN_DETECTOR_JOB_DELAY)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToNewUrlThenLoginDetected() = coroutineRule.runBlocking {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

        loginDetector.onEvent(NavigationEvent.WebNavigationEvent(WebNavigationStateChange.UrlUpdated(url = "http://example.com")))
        loginDetector.onEvent(NavigationEvent.PageFinished)
        delay(LOGIN_DETECTOR_JOB_DELAY)

        assertEvent<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToSameUrlThenLoginNotDetected() = coroutineRule.runBlocking {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

        loginDetector.onEvent(NavigationEvent.WebNavigationEvent(WebNavigationStateChange.UrlUpdated(url = "http://example.com/login")))
        loginDetector.onEvent(NavigationEvent.PageFinished)
        delay(LOGIN_DETECTOR_JOB_DELAY)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenNotDetectedLoginAttemptAndForwardedToNewUrlThenLoginNotDetected() = coroutineRule.runBlocking {
        loginDetector.onEvent(NavigationEvent.WebNavigationEvent(WebNavigationStateChange.UrlUpdated(url = "http://example.com")))
        loginDetector.onEvent(NavigationEvent.PageFinished)
        delay(LOGIN_DETECTOR_JOB_DELAY)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndNextPageFinishedThenLoadingNewPageDoesNotDetectLogin() = coroutineRule.runBlocking {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
        loginDetector.onEvent(NavigationEvent.PageFinished)

        loginDetector.onEvent(NavigationEvent.WebNavigationEvent(NewPage(url = "http://another.example.com", title = "")))
        loginDetector.onEvent(NavigationEvent.PageFinished)
        delay(LOGIN_DETECTOR_JOB_DELAY)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserNavigatesBackThenNewPageDoesNotDetectLogin() = coroutineRule.runBlocking {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
        loginDetector.onEvent(NavigationEvent.UserAction.NavigateBack)

        fullyLoadSite("http://another.example.com")
        delay(LOGIN_DETECTOR_JOB_DELAY)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserNavigatesForwardThenNewPageDoesNotDetectLogin() = coroutineRule.runBlocking {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
        loginDetector.onEvent(NavigationEvent.UserAction.NavigateForward)

        fullyLoadSite("http://another.example.com")
        delay(LOGIN_DETECTOR_JOB_DELAY)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserReloadsWebsiteThenNewPageDoesNotDetectLogin() = coroutineRule.runBlocking {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
        loginDetector.onEvent(NavigationEvent.UserAction.Refresh)

        fullyLoadSite("http://another.example.com")
        delay(LOGIN_DETECTOR_JOB_DELAY)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserSubmitsNewQueryThenNewPageDoesNotDetectLogin() = coroutineRule.runBlocking {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
        loginDetector.onEvent(NavigationEvent.UserAction.NewQuerySubmitted)

        fullyLoadSite("http://another.example.com")
        delay(LOGIN_DETECTOR_JOB_DELAY)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedHasInvalidURLThenNewPageDoesNotDetectLogin() = coroutineRule.runBlocking {
        loginDetector.onEvent(NavigationEvent.LoginAttempt(""))

        fullyLoadSite("http://example.com")
        delay(LOGIN_DETECTOR_JOB_DELAY)

        assertEventNotIssued<LoginDetected>()
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToInvalidNewPageThenLoginDetected() = coroutineRule.runBlocking {
        loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

        fullyLoadSite("")
        delay(LOGIN_DETECTOR_JOB_DELAY)

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

    private fun redirectTo(url: String) {
        loginDetector.onEvent(NavigationEvent.Redirect(url = url))
        loginDetector.onEvent(NavigationEvent.WebNavigationEvent(NewPage(url = url, title = "")))
        loginDetector.onEvent(NavigationEvent.PageFinished)
    }

    companion object {
        const val LOGIN_DETECTOR_JOB_DELAY = 1000L
    }
}
