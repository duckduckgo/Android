/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.survey.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.core.net.toUri
import androidx.lifecycle.Observer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.survey.api.SurveyRepository
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.model.Survey.Status.DONE
import com.duckduckgo.app.survey.model.Survey.Status.SCHEDULED
import com.duckduckgo.app.survey.ui.SurveyActivity.Companion.SurveySource
import com.duckduckgo.app.survey.ui.SurveyViewModel.Command
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SurveyViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineTestRule = CoroutineTestRule()

    private var mockCommandObserver: Observer<Command> = mock()

    private var mockAppInstallStore: AppInstallStore = mock()

    private var mockStatisticsStore: StatisticsDataStore = mock()

    private var mockAppBuildConfig: AppBuildConfig = mock()

    private val mockAppDaysUsedRepository: AppDaysUsedRepository = mock()

    private val mockSurveyRepository: SurveyRepository = mock()

    private lateinit var testee: SurveyViewModel
    private val testSource = SurveySource.IN_APP

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        runBlocking {
            whenever(mockAppDaysUsedRepository.getLastActiveDay()).thenReturn("today")
            whenever(mockAppBuildConfig.versionName).thenReturn("name")

            testee = SurveyViewModel(
                mockStatisticsStore,
                mockAppInstallStore,
                mockAppBuildConfig,
                coroutineTestRule.testDispatcherProvider,
                mockAppDaysUsedRepository,
                mockSurveyRepository,
            )
            testee.command.observeForever(mockCommandObserver)
        }
    }

    @After
    fun after() {
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenViewStartedThenSurveyLoaded() {
        val url = "https://survey.com"
        val captor = argumentCaptor<Command.LoadSurvey>()

        testee.start(Survey("", url, null, SCHEDULED), testSource)
        verify(mockCommandObserver).onChanged(captor.capture())
        assertTrue(captor.lastValue.url.contains(url))
    }

    @Test
    fun whenSurveyStartedThenParametersAddedToUrl() {
        whenever(mockStatisticsStore.atb).thenReturn(Atb("123"))
        whenever(mockStatisticsStore.variant).thenReturn("abc")
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))
        whenever(mockAppBuildConfig.sdkInt).thenReturn(16)
        whenever(mockAppBuildConfig.manufacturer).thenReturn("pixel")

        val captor = argumentCaptor<Command.LoadSurvey>()
        testee.start(Survey("", "https://survey.com", null, SCHEDULED), testSource)
        verify(mockCommandObserver).onChanged(captor.capture())
        val loadedUri = captor.lastValue.url.toUri()

        assertEquals("123", loadedUri.getQueryParameter("atb"))
        assertEquals("abc", loadedUri.getQueryParameter("var"))
        assertEquals("2", loadedUri.getQueryParameter("delta"))
        assertEquals("16", loadedUri.getQueryParameter("av"))
        assertEquals("name", loadedUri.getQueryParameter("ddgv"))
        assertEquals("pixel", loadedUri.getQueryParameter("man"))
        assertEquals("in_app", loadedUri.getQueryParameter("src"))
        assertEquals("today", loadedUri.getQueryParameter("da"))
    }

    @Test
    fun whenSurveyStartedFromNotificationThenSourceIsPushAndDayActiveIsYesterday() = runTest {
        whenever(mockStatisticsStore.atb).thenReturn(Atb("123"))
        whenever(mockStatisticsStore.variant).thenReturn("abc")
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))
        whenever(mockAppBuildConfig.sdkInt).thenReturn(16)
        whenever(mockAppBuildConfig.manufacturer).thenReturn("pixel")
        whenever(mockAppDaysUsedRepository.getPreviousActiveDay()).thenReturn("yesterday")

        val captor = argumentCaptor<Command.LoadSurvey>()
        testee.start(Survey("", "https://survey.com", null, SCHEDULED), SurveySource.PUSH)
        verify(mockCommandObserver).onChanged(captor.capture())
        val loadedUri = captor.lastValue.url.toUri()

        assertEquals("123", loadedUri.getQueryParameter("atb"))
        assertEquals("abc", loadedUri.getQueryParameter("var"))
        assertEquals("2", loadedUri.getQueryParameter("delta"))
        assertEquals("16", loadedUri.getQueryParameter("av"))
        assertEquals("name", loadedUri.getQueryParameter("ddgv"))
        assertEquals("pixel", loadedUri.getQueryParameter("man"))
        assertEquals("push", loadedUri.getQueryParameter("src"))
        assertEquals("yesterday", loadedUri.getQueryParameter("da"))
    }

    @Test
    fun whenSurveyStartedAndInAppTpRetentionStudyThenParametersAddedToUrl() {
        whenever(mockStatisticsStore.atb).thenReturn(Atb("123"))
        whenever(mockStatisticsStore.variant).thenReturn("abc")
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))
        whenever(mockAppBuildConfig.sdkInt).thenReturn(16)
        whenever(mockAppBuildConfig.manufacturer).thenReturn("pixel")
        whenever(mockAppBuildConfig.model).thenReturn("XL")

        val captor = argumentCaptor<Command.LoadSurvey>()
        testee.start(Survey("", "https://survey.com", null, SCHEDULED), testSource)
        verify(mockCommandObserver).onChanged(captor.capture())
        val loadedUri = captor.lastValue.url.toUri()

        assertEquals("123", loadedUri.getQueryParameter("atb"))
        assertEquals("abc", loadedUri.getQueryParameter("var"))
        assertEquals("2", loadedUri.getQueryParameter("delta"))
        assertEquals("16", loadedUri.getQueryParameter("av"))
        assertEquals("name", loadedUri.getQueryParameter("ddgv"))
        assertEquals("pixel", loadedUri.getQueryParameter("man"))
        assertEquals("XL", loadedUri.getQueryParameter("mo"))
    }

    @Test
    fun whenSurveyFailsToLoadThenErrorShown() {
        testee.onSurveyFailedToLoad()
        verify(mockCommandObserver).onChanged(Command.ShowError)
    }

    @Test
    fun whenSurveyLoadedThenSurveyIsShown() {
        testee.onSurveyLoaded()
        verify(mockCommandObserver).onChanged(Command.ShowSurvey)
    }

    @Test
    fun whenSurveyCompletedThenViewIsClosedAndRecordIsUpdatedAnd() {
        testee.start(Survey("", "https://survey.com", null, SCHEDULED), testSource)
        testee.onSurveyCompleted()
        verify(mockSurveyRepository).updateSurvey(Survey("", "https://survey.com", null, DONE))
        verify(mockCommandObserver).onChanged(Command.Close)
    }

    @Test
    fun whenSurveyCompletedThenSurveyNotificationIsCleared() {
        testee.start(Survey("", "https://survey.com", null, SCHEDULED), testSource)
        testee.onSurveyCompleted()
        verify(mockSurveyRepository).clearSurveyNotification()
    }

    @Test
    fun whenSurveyDismissedThenViewIsClosedAndRecordIsNotUpdated() {
        testee.start(Survey("", "https://survey.com", null, SCHEDULED), testSource)
        testee.onSurveyDismissed()
        verify(mockSurveyRepository, never()).updateSurvey(any())
        verify(mockCommandObserver).onChanged(Command.Close)
    }
}
