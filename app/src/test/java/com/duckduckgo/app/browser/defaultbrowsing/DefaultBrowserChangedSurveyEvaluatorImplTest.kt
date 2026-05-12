/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.browser.defaultbrowsing

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.survey.ui.SurveyActivity
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.modalcoordinator.api.ModalEvaluator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class DefaultBrowserChangedSurveyEvaluatorImplTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val applicationContext: Application = ApplicationProvider.getApplicationContext()
    private val surveyManager: DefaultBrowserChangedSurveyManager = mock()

    private val testee = DefaultBrowserChangedSurveyEvaluatorImpl(
        appCoroutineScope = coroutinesTestRule.testScope,
        applicationContext = applicationContext,
        surveyManager = surveyManager,
        dispatchers = coroutinesTestRule.testDispatcherProvider,
    )

    @Before
    fun setUp() {
        shadowOf(applicationContext).clearNextStartedActivities()
    }

    @Test
    fun whenSurveyShouldNotTriggerThenEvaluationIsSkipped() = runTest {
        whenever(surveyManager.shouldTriggerSurvey()).thenReturn(false)

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.Skipped, result)
        verify(surveyManager, never()).markSurveyShown()
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()
        assertNull(shadowOf(applicationContext).nextStartedActivity)
    }

    @Test
    fun whenSurveyShouldTriggerThenEvaluationReturnsModalShown() = runTest {
        whenever(surveyManager.shouldTriggerSurvey()).thenReturn(true)
        whenever(surveyManager.buildSurveyUrl("in-app")).thenReturn("https://example.com/survey")

        val result = testee.evaluate()

        assertEquals(ModalEvaluator.EvaluationResult.ModalShown, result)
    }

    @Test
    fun whenSurveyShouldTriggerThenSurveyIsMarkedShown() = runTest {
        whenever(surveyManager.shouldTriggerSurvey()).thenReturn(true)
        whenever(surveyManager.buildSurveyUrl("in-app")).thenReturn("https://example.com/survey")

        testee.evaluate()

        verify(surveyManager).markSurveyShown()
    }

    @Test
    fun whenSurveyShouldTriggerThenSurveyActivityIsStartedWithExpectedFlags() = runTest {
        whenever(surveyManager.shouldTriggerSurvey()).thenReturn(true)
        whenever(surveyManager.buildSurveyUrl("in-app")).thenReturn("https://example.com/survey")

        testee.evaluate()
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        val startedIntent = shadowOf(applicationContext).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(SurveyActivity::class.java.name, startedIntent.component?.className)
        assertTrue(startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(startedIntent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test
    fun evaluatorHasExpectedPriority() {
        assertEquals(1, testee.priority)
    }

    @Test
    fun evaluatorHasExpectedId() {
        assertEquals("default_browser_changed_survey_evaluator", testee.evaluatorId)
    }
}
