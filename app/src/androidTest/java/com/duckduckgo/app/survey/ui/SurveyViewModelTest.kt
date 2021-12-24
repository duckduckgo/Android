/*
 * Copyright (c) 2019 DuckDuckGo
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

import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.core.net.toUri
import androidx.lifecycle.Observer
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.model.Survey.Status.DONE
import com.duckduckgo.app.survey.model.Survey.Status.SCHEDULED
import com.duckduckgo.app.survey.ui.SurveyViewModel.Command
import org.mockito.kotlin.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockitoAnnotations
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class SurveyViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutineTestRule = CoroutineTestRule()

    private var mockCommandObserver: Observer<Command> = mock()

    private var mockSurveyDao: SurveyDao = mock()

    private var mockAppInstallStore: AppInstallStore = mock()

    private var mockStatisticsStore: StatisticsDataStore = mock()

    private lateinit var testee: SurveyViewModel

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        testee = SurveyViewModel(mockSurveyDao, mockStatisticsStore, mockAppInstallStore, coroutineTestRule.testDispatcherProvider)
        testee.command.observeForever(mockCommandObserver)
    }

    @After
    fun after() {
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenViewStartedThenSurveyLoaded() {
        val url = "https://survey.com"
        val captor = argumentCaptor<Command.LoadSurvey>()

        testee.start(Survey("", url, null, SCHEDULED))
        verify(mockCommandObserver).onChanged(captor.capture())
        assertTrue(captor.lastValue.url.contains(url))
    }

    @Test
    fun whenSurveyStartedThenParametersAddedToUrl() {
        whenever(mockStatisticsStore.atb).thenReturn(Atb("123"))
        whenever(mockStatisticsStore.variant).thenReturn("abc")
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))

        val captor = argumentCaptor<Command.LoadSurvey>()
        testee.start(Survey("", "https://survey.com", null, SCHEDULED))
        verify(mockCommandObserver).onChanged(captor.capture())
        val loadedUri = captor.lastValue.url.toUri()

        assertEquals("123", loadedUri.getQueryParameter("atb"))
        assertEquals("abc", loadedUri.getQueryParameter("var"))
        assertEquals("2", loadedUri.getQueryParameter("delta"))
        assertEquals("${Build.VERSION.SDK_INT}", loadedUri.getQueryParameter("av"))
        assertEquals(BuildConfig.VERSION_NAME, loadedUri.getQueryParameter("ddgv"))
        assertEquals(Build.MANUFACTURER, loadedUri.getQueryParameter("man"))
        assertEquals(Build.MODEL, loadedUri.getQueryParameter("mo"))
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
        testee.start(Survey("", "https://survey.com", null, SCHEDULED))
        testee.onSurveyCompleted()
        verify(mockSurveyDao).update(Survey("", "https://survey.com", null, DONE))
        verify(mockCommandObserver).onChanged(Command.Close)
    }

    @Test
    fun whenSurveyDismissedThenViewIsClosedAndRecordIsNotUpdated() {
        testee.start(Survey("", "https://survey.com", null, SCHEDULED))
        testee.onSurveyDismissed()
        verify(mockSurveyDao, never()).update(any())
        verify(mockCommandObserver).onChanged(Command.Close)
    }
}
