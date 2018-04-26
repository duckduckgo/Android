/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.settings

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import android.content.Context
import android.net.MailTo
import android.support.test.InstrumentationRegistry
import com.duckduckgo.app.blockingObserve
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultBrowsing.DefaultBrowserDetector
import com.duckduckgo.app.global.AndroidStringResolver
import com.duckduckgo.app.global.StringResolver
import com.duckduckgo.app.settings.SettingsViewModel.Command
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.nhaarman.mockito_kotlin.KArgumentCaptor
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class SettingsViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var testee: SettingsViewModel

    private lateinit var stringResolver: StringResolver

    private lateinit var context: Context

    @Mock
    private lateinit var commandObserver: Observer<Command>

    @Mock
    private lateinit var mockAppSettingsDataStore: SettingsDataStore

    @Mock
    private lateinit var mockDefaultBrowserDetector: DefaultBrowserDetector

    private lateinit var commandCaptor: KArgumentCaptor<Command>

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)

        context = InstrumentationRegistry.getTargetContext()

        stringResolver = AndroidStringResolver(context)
        commandCaptor = argumentCaptor()


        testee = SettingsViewModel(stringResolver, mockAppSettingsDataStore, mockDefaultBrowserDetector)
        testee.command.observeForever(commandObserver)
    }

    @Test
    fun whenStartNotCalledYetThenViewStateInitialisedDefaultValues() {
        assertNotNull(testee.viewState)

        val value = latestViewState()
        assertTrue(value.loading)
        assertEquals("", value.version)
        assertTrue(value.autoCompleteSuggestionsEnabled)
        assertFalse(value.showDefaultBrowserSetting)
        assertFalse(value.isAppDefaultBrowser)
    }

    @Test
    fun whenStartCalledThenLoadingSetToFalse() {
        testee.start()
        val value = latestViewState()
        assertEquals(false, value.loading)
    }

    @Test
    fun whenStartCalledThenVersionSetCorrectly() {
        testee.start()
        val value = latestViewState()
        assertEquals("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", value.version)
    }

    @Test
    fun whenLeaveFeedBackRequestedThenUriContainsCorrectEmail() {
        testee.userRequestedToSendFeedback()
        val uri = retrieveObservedUri()
        assertEquals("android@duckduckgo.com", uri.to)
    }

    @Test
    fun whenLeaveFeedBackRequestedThenUriContainsCorrectSubject() {
        testee.userRequestedToSendFeedback()
        val uri = retrieveObservedUri()
        assertEquals(context.getString(R.string.feedbackSubject), uri.subject)
    }

    @Test
    fun whenDefaultBrowserAppAlreadySetToOursThenIsDefaultBrowserFlagIsTrue() {
        whenever(mockDefaultBrowserDetector.isCurrentlyConfiguredAsDefaultBrowser()).thenReturn(true)
        testee.start()
        val viewState = latestViewState()
        assertTrue(viewState.isAppDefaultBrowser)
    }


    @Test
    fun whenDefaultBrowserAppNotSetToOursThenIsDefaultBrowserFlagIsFalse() {
        whenever(mockDefaultBrowserDetector.isCurrentlyConfiguredAsDefaultBrowser()).thenReturn(false)
        testee.start()
        val viewState = latestViewState()
        assertFalse(viewState.isAppDefaultBrowser)
    }

    @Test
    fun whenBrowserDetectorIndicatesDefaultCannotBeSetThenFlagToShowSettingIsFalse() {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(false)
        testee.start()
        assertFalse(latestViewState().showDefaultBrowserSetting)
    }

    @Test
    fun whenBrowserDetectorIndicatesDefaultCanBeSetThenFlagToShowSettingIsTrue() {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(true)
        testee.start()
        assertTrue(latestViewState().showDefaultBrowserSetting)
    }

    private fun latestViewState() = testee.viewState.value!!

    private fun retrieveObservedUri(): MailTo {
        testee.command.blockingObserve()
        verify(commandObserver).onChanged(commandCaptor.capture())

        val capturedCommand = commandCaptor.firstValue as Command.SendEmail
        return MailTo.parse(capturedCommand.emailUri.toString())
    }
}