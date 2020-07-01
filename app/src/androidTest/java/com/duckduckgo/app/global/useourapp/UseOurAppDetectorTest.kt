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

package com.duckduckgo.app.global.useourapp

import android.webkit.WebView
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.logindetection.WebNavigationEvent
import com.duckduckgo.app.global.events.db.UserEventEntity
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class UseOurAppDetectorTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var testee: UseOurAppDetector

    private val mockUserEventsStore: UserEventsStore = mock()

    @Before
    fun setup() {
        testee = UseOurAppDetector(mockUserEventsStore)
    }

    @Test
    fun whenCheckingIfUrlIsFromUseOurAppDomainThenReturnTrue() {
        assertTrue(testee.isUseOurAppUrl("http://www.facebook.com"))
    }

    @Test
    fun whenCheckingIfMobileUrlIsFromUseOurAppDomainThenReturnTrue() {
        assertTrue(testee.isUseOurAppUrl("http://m.facebook.com"))
    }

    @Test
    fun whenCheckingIfMobileOnlyDomainIsFromUseOurAppDomainThenReturnTrue() {
        assertTrue(testee.isUseOurAppUrl("m.facebook.com"))
    }

    @Test
    fun whenCheckingIfOnlyDomainUrlIsFromUseOurAppDomainThenReturnTrue() {
        assertTrue(testee.isUseOurAppUrl("facebook.com"))
    }

    @Test
    fun whenCheckingIfUrlIsFromUseOurAppDomainThenReturnFalse() {
        assertFalse(testee.isUseOurAppUrl("http://example.com"))
    }

    @Test
    fun whenAllowLoginDetectionAndShortcutNotAddedThenReturnFalse() = coroutineRule.runBlocking {
        val webView: WebView = mock()
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)).thenReturn(null)

        assertFalse(testee.allowLoginDetection(WebNavigationEvent.OnPageStarted(webView)))
    }

    @Test
    fun whenAllowLoginDetectionAndFireProofAlreadySeenThenReturnFalse() = coroutineRule.runBlocking {
        val webView: WebView = mock()
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)).thenReturn(UserEventEntity(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED))
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN)).thenReturn(UserEventEntity(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN))

        assertFalse(testee.allowLoginDetection(WebNavigationEvent.OnPageStarted(webView)))
    }

    @Test
    fun whenAllowLoginDetectionWithOnPageStartedEventAndUrlIsUseOurAppThenReturnTrue() = coroutineRule.runBlocking {
        val webView: WebView = mock()
        whenever(webView.url).thenReturn("http://m.facebook.com")
        givenShortcutIsAddedAndFireproofNotSeen()

        assertTrue(testee.allowLoginDetection(WebNavigationEvent.OnPageStarted(webView)))
    }

    @Test
    fun whenAllowLoginDetectionWithOnPageStartedEventAndUrlIsNotUseOurAppThenReturnFalse() = coroutineRule.runBlocking {
        val webView: WebView = mock()
        whenever(webView.url).thenReturn("http://example.com")
        givenShortcutIsAddedAndFireproofNotSeen()

        assertFalse(testee.allowLoginDetection(WebNavigationEvent.OnPageStarted(webView)))
    }

    @Test
    fun whenAllowLoginDetectionWithShouldInterceptEventAndUrlIsUseOurAppThenReturnTrue() = coroutineRule.runBlocking {
        val webView: WebView = mock()
        whenever(webView.url).thenReturn("http://m.facebook.com")
        givenShortcutIsAddedAndFireproofNotSeen()

        assertTrue(testee.allowLoginDetection(WebNavigationEvent.ShouldInterceptRequest(webView, mock())))
    }

    @Test
    fun whenAllowLoginDetectionWithOnShouldInterceptEventAndUrlIsNotUseOurAppThenReturnFalse() = coroutineRule.runBlocking {
        val webView: WebView = mock()
        whenever(webView.url).thenReturn("http://example.com")
        givenShortcutIsAddedAndFireproofNotSeen()

        assertFalse(testee.allowLoginDetection(WebNavigationEvent.ShouldInterceptRequest(webView, mock())))
    }

    @Test
    fun whenRegisterIfFireproofSeenForTheFirstTimeAndUrlIsUseOurAppThenRegisterUserEvent() = coroutineRule.runBlocking {
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN)).thenReturn(null)

        testee.registerIfFireproofSeenForTheFirstTime("http://m.facebook.com")

        verify(mockUserEventsStore).registerUserEvent(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN)
    }

    @Test
    fun whenRegisterIfFireproofSeenForTheFirstTimeAndUrlIsNotUseOurAppThenDoNotRegisterUserEvent() = coroutineRule.runBlocking {
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN)).thenReturn(null)

        testee.registerIfFireproofSeenForTheFirstTime("example.com")

        verify(mockUserEventsStore, never()).registerUserEvent(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN)
    }

    @Test
    fun whenRegisterIfFireproofSeenForTheFirstTimeButAlreadySeenThenDoNotRegisterUserEvent() = coroutineRule.runBlocking {
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN)).thenReturn(UserEventEntity(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN))

        testee.registerIfFireproofSeenForTheFirstTime("http://m.facebook.com")

        verify(mockUserEventsStore, never()).registerUserEvent(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN)
    }

    private suspend fun givenShortcutIsAddedAndFireproofNotSeen() {
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED)).thenReturn(UserEventEntity(UserEventKey.USE_OUR_APP_SHORTCUT_ADDED))
        whenever(mockUserEventsStore.getUserEvent(UserEventKey.USE_OUR_APP_FIREPROOF_DIALOG_SEEN)).thenReturn(null)
    }
}
