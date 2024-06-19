/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.survey.rmf

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.JsonMessageAction
import com.duckduckgo.survey.api.SurveyParameterManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SurveyActionMapperTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val surveyParameterManager: SurveyParameterManager = mock()

    private val testee = SurveyActionMapper(
        coroutineTestRule.testDispatcherProvider,
        surveyParameterManager,
    )

    @Test
    fun whenEvaluateAndTypeIsSurveyThenReturnActionSurvey() = runTest {
        whenever(surveyParameterManager.buildSurveyUrlStrict(any(), any())).thenReturn("resolved")
        val jsonMessageAction = JsonMessageAction(
            type = "survey",
            value = "http://example.com",
            additionalParameters = mapOf("queryParams" to "atb;var;delta;av;ddgv;man;mo;da;src"),
        )
        val action = testee.evaluate(jsonMessageAction)
        assertTrue(action is Action.Survey)
    }

    @Test
    fun whenEvaluateAndTypeIsSurveyAndNoParamsThenReturnActionSurvey() {
        val jsonMessageAction = JsonMessageAction(
            type = "survey",
            value = "http://example.com",
            additionalParameters = emptyMap(),
        )
        val action = testee.evaluate(jsonMessageAction)
        assertTrue(action is Action.Survey)
    }

    @Test
    fun whenEvaluateAndTypeIsSurveyButUnknownParamsThenReturnNull() = runTest {
        whenever(surveyParameterManager.buildSurveyUrlStrict(any(), any())).thenReturn(null)
        val jsonMessageAction = JsonMessageAction(
            type = "survey",
            value = "value",
            additionalParameters = mapOf("queryParams" to "fake;lies;false"),
        )
        val action = testee.evaluate(jsonMessageAction)
        assertNull(action)
    }

    @Test
    fun whenEvaluateAndTypeIsNotSurveyThenReturnNull() {
        val jsonMessageAction = JsonMessageAction(
            type = "notSurvey",
            value = "value",
            additionalParameters = mapOf("queryParams" to "param1;param2"),
        )
        val action = testee.evaluate(jsonMessageAction)
        assertNull(action)
    }
}
