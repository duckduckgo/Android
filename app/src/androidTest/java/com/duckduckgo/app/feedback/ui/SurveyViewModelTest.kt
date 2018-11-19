/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.feedback.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.feedback.db.SurveyDao
import com.duckduckgo.app.feedback.model.Survey
import com.duckduckgo.app.feedback.model.Survey.Status.DONE
import com.duckduckgo.app.feedback.model.Survey.Status.SCHEDULED
import com.duckduckgo.app.feedback.ui.SurveyViewModel.Command
import com.nhaarman.mockito_kotlin.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockitoAnnotations

class SurveyViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    private var mockCommandObserver: Observer<Command> = mock()

    private var mockSurveyDao: SurveyDao = mock()

    private lateinit var testee: SurveyViewModel

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)
        testee = SurveyViewModel(mockSurveyDao)
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
        assertEquals(url, captor.lastValue.url)
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