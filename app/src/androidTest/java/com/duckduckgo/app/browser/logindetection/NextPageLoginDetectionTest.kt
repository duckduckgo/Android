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
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class NextPageLoginDetectionTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    @get:Rule @Suppress("unused") var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val loginDetector = NextPageLoginDetection(mockSettingsDataStore, TestCoroutineScope())

    private val loginObserver = mock<Observer<LoginDetected>>()
    private val loginEventCaptor = argumentCaptor<LoginDetected>()

    @Before
    fun setup() {
        loginDetector.loginEventLiveData.observeForever(loginObserver)
    }

    @Test
    fun whenLoginAttemptedAndUserForwardedToNewPageThenLoginDetected() =
        coroutineRule.runBlocking {
            givenLoginDetector(enabled = true)
            loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

            redirectTo("http://example.com")
            giveLoginDetectorChanceToExecute()

            assertEvent<LoginDetected>()
        }

    @Test
    fun whenLoginAttemptedInsideOAuthFlowThenLoginDetectedWhenUserForwardedToDifferentDomain() =
        coroutineRule.runBlocking {
            givenLoginDetector(enabled = true)
            redirectTo("https://accounts.google.com/o/oauth2/v2/auth")
            giveLoginDetectorChanceToExecute()
            redirectTo("https://accounts.google.com/signin/v2/challenge/pwd")
            giveLoginDetectorChanceToExecute()
            loginDetector.onEvent(
                NavigationEvent.LoginAttempt("https://accounts.google.com/signin/v2/challenge"))
            redirectTo("https://accounts.google.com/signin/v2/challenge/az?client_id")
            giveLoginDetectorChanceToExecute()
            redirectTo("https://accounts.google.com/randomPath")
            giveLoginDetectorChanceToExecute()
            redirectTo("http://example.com")
            giveLoginDetectorChanceToExecute()

            assertEvent<LoginDetected> { assertEquals("example.com", this.forwardedToDomain) }
        }

    @Test
    fun whenLoginAttemptedInsideSSOFlowThenLoginDetectedWhenUserForwardedToDifferentDomain() =
        coroutineRule.runBlocking {
            givenLoginDetector(enabled = true)
            fullyLoadSite("https://app.asana.com/-/login")
            giveLoginDetectorChanceToExecute()
            redirectTo("https://sso.host.com/saml2/idp/SSOService.php")
            giveLoginDetectorChanceToExecute()
            fullyLoadSite("https://sso.host.com/module.php/core/loginuserpass.php")
            giveLoginDetectorChanceToExecute()
            loginDetector.onEvent(
                NavigationEvent.LoginAttempt(
                    "https://sso.host.com/module.php/core/loginuserpass.php"))
            redirectTo("https://sso.host.com/module.php/duosecurity/getduo.php")
            giveLoginDetectorChanceToExecute()
            redirectTo("https://app.asana.com/")
            giveLoginDetectorChanceToExecute()

            assertEvent<LoginDetected> { assertEquals("app.asana.com", this.forwardedToDomain) }
        }

    @Test
    fun whenLoginAttemptedSkip2FAUrlsThenLoginDetectedForLatestOne() =
        coroutineRule.runBlocking {
            givenLoginDetector(enabled = true)
            fullyLoadSite("https://accounts.google.com/ServiceLogin")
            giveLoginDetectorChanceToExecute()
            fullyLoadSite("https://accounts.google.com/signin/v2/challenge/pwd")
            giveLoginDetectorChanceToExecute()
            loginDetector.onEvent(
                NavigationEvent.LoginAttempt("https://accounts.google.com/signin/v2/challenge/pwd"))
            redirectTo("https://accounts.google.com/signin/v2/challenge/az")
            giveLoginDetectorChanceToExecute()
            redirectTo("https://mail.google.com/mail")
            giveLoginDetectorChanceToExecute()

            assertEvent<LoginDetected> { assertEquals("mail.google.com", this.forwardedToDomain) }
        }

    @Test
    fun whenLoginAttemptedAndUserForwardedToMultipleNewPagesThenLoginDetectedForLatestOne() =
        coroutineRule.runBlocking {
            givenLoginDetector(enabled = true)
            loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

            fullyLoadSite("http://example.com")
            fullyLoadSite("http://example2.com")
            fullyLoadSite("http://example3.com")
            giveLoginDetectorChanceToExecute()

            assertEvent<LoginDetected> { assertEquals("example3.com", this.forwardedToDomain) }
        }

    @Test
    fun whenLoginAttemptedAndUserForwardedToSamePageThenLoginNotDetected() =
        coroutineRule.runBlocking {
            givenLoginDetector(enabled = true)
            loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

            fullyLoadSite("http://example.com/login")
            giveLoginDetectorChanceToExecute()

            assertEventNotIssued<LoginDetected>()
        }

    @Test
    fun whenNotDetectedLoginAttemptAndForwardedToNewPageThenLoginNotDetected() =
        coroutineRule.runBlocking {
            givenLoginDetector(enabled = true)
            fullyLoadSite("http://example.com")
            giveLoginDetectorChanceToExecute()

            assertEventNotIssued<LoginDetected>()
        }

    @Test
    fun whenLoginAttemptedAndUserForwardedToNewUrlThenLoginDetected() =
        coroutineRule.runBlocking {
            givenLoginDetector(enabled = true)
            loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

            loginDetector.onEvent(
                NavigationEvent.WebNavigationEvent(
                    WebNavigationStateChange.UrlUpdated(url = "http://example.com")))
            loginDetector.onEvent(NavigationEvent.PageFinished)
            giveLoginDetectorChanceToExecute()

            assertEvent<LoginDetected>()
        }

    @Test
    fun whenLoginAttemptedAndUserForwardedToSameUrlThenLoginNotDetected() =
        coroutineRule.runBlocking {
            givenLoginDetector(enabled = true)
            loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

            loginDetector.onEvent(
                NavigationEvent.WebNavigationEvent(
                    WebNavigationStateChange.UrlUpdated(url = "http://example.com/login")))
            loginDetector.onEvent(NavigationEvent.PageFinished)
            giveLoginDetectorChanceToExecute()

            assertEventNotIssued<LoginDetected>()
        }

    @Test
    fun whenNotDetectedLoginAttemptAndForwardedToNewUrlThenLoginNotDetected() =
        coroutineRule.runBlocking {
            givenLoginDetector(enabled = true)
            loginDetector.onEvent(
                NavigationEvent.WebNavigationEvent(
                    WebNavigationStateChange.UrlUpdated(url = "http://example.com")))
            loginDetector.onEvent(NavigationEvent.PageFinished)
            giveLoginDetectorChanceToExecute()

            assertEventNotIssued<LoginDetected>()
        }

    @Test
    fun whenLoginAttemptedAndNextPageFinishedThenLoadingNewPageDoesNotDetectLogin() =
        coroutineRule.runBlocking {
            givenLoginDetector(enabled = true)
            loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
            loginDetector.onEvent(NavigationEvent.PageFinished)

            loginDetector.onEvent(
                NavigationEvent.WebNavigationEvent(
                    NewPage(url = "http://another.example.com", title = "")))
            loginDetector.onEvent(NavigationEvent.PageFinished)
            giveLoginDetectorChanceToExecute()

            assertEventNotIssued<LoginDetected>()
        }

    @Test
    fun whenLoginAttemptedAndUserNavigatesBackThenNewPageDoesNotDetectLogin() =
        coroutineRule.runBlocking {
            givenLoginDetector(enabled = true)
            loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
            loginDetector.onEvent(NavigationEvent.UserAction.NavigateBack)

            fullyLoadSite("http://another.example.com")
            giveLoginDetectorChanceToExecute()

            assertEventNotIssued<LoginDetected>()
        }

    @Test
    fun whenLoginAttemptedAndUserNavigatesForwardThenNewPageDoesNotDetectLogin() =
        coroutineRule.runBlocking {
            givenLoginDetector(enabled = true)
            loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
            loginDetector.onEvent(NavigationEvent.UserAction.NavigateForward)

            fullyLoadSite("http://another.example.com")
            giveLoginDetectorChanceToExecute()

            assertEventNotIssued<LoginDetected>()
        }

    @Test
    fun whenLoginAttemptedAndUserReloadsWebsiteThenNewPageDoesNotDetectLogin() =
        coroutineRule.runBlocking {
            givenLoginDetector(enabled = true)
            loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
            loginDetector.onEvent(NavigationEvent.UserAction.Refresh)

            fullyLoadSite("http://another.example.com")
            giveLoginDetectorChanceToExecute()

            assertEventNotIssued<LoginDetected>()
        }

    @Test
    fun whenLoginAttemptedAndUserSubmitsNewQueryThenNewPageDoesNotDetectLogin() =
        coroutineRule.runBlocking {
            givenLoginDetector(enabled = true)
            loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))
            loginDetector.onEvent(NavigationEvent.UserAction.NewQuerySubmitted)

            fullyLoadSite("http://another.example.com")
            giveLoginDetectorChanceToExecute()

            assertEventNotIssued<LoginDetected>()
        }

    @Test
    fun whenLoginAttemptedHasInvalidURLThenNewPageDoesNotDetectLogin() =
        coroutineRule.runBlocking {
            givenLoginDetector(enabled = true)
            loginDetector.onEvent(NavigationEvent.LoginAttempt(""))

            fullyLoadSite("http://example.com")
            giveLoginDetectorChanceToExecute()

            assertEventNotIssued<LoginDetected>()
        }

    @Test
    fun whenLoginAttemptedAndUserForwardedToInvalidNewPageThenLoginDetected() =
        coroutineRule.runBlocking {
            givenLoginDetector(enabled = true)
            loginDetector.onEvent(NavigationEvent.LoginAttempt("http://example.com/login"))

            fullyLoadSite("")
            giveLoginDetectorChanceToExecute()

            assertEventNotIssued<LoginDetected>()
        }

    private suspend fun giveLoginDetectorChanceToExecute() {
        delay(LOGIN_DETECTOR_JOB_DELAY)
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

    private fun givenLoginDetector(enabled: Boolean) {
        whenever(mockSettingsDataStore.appLoginDetection).thenReturn(enabled)
    }

    companion object {
        const val LOGIN_DETECTOR_JOB_DELAY = 1000L
    }
}
