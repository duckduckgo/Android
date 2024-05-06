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

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.JsonMessageAction
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SurveyActionMapperTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private var mockAppInstallStore: AppInstallStore = mock()
    private var mockStatisticsStore: StatisticsDataStore = mock()
    private var mockAppBuildConfig: AppBuildConfig = mock()
    private val mockAppDaysUsedRepository: AppDaysUsedRepository = mock()

    private val testee = SurveyActionMapper(
        mockStatisticsStore,
        mockAppInstallStore,
        mockAppBuildConfig,
        mockAppDaysUsedRepository,
        coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenEvaluateAndTypeIsSurveyThenReturnActionSurvey() {
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
    fun whenEvaluateAndTypeIsSurveyButUnknownParamsThenReturnNull() {
        val jsonMessageAction = JsonMessageAction(
            type = "survey",
            value = "value",
            additionalParameters = mapOf("queryParams" to "fake;lies;false"),
        )
        val action = testee.evaluate(jsonMessageAction)
        assertNull(action)
    }

    @Test
    fun whenEvaluateAndQueryParamsInPayloadThenReturnActionSurveyWithQueryParams() = runTest {
        whenever(mockStatisticsStore.atb).thenReturn(Atb("123"))
        whenever(mockStatisticsStore.variant).thenReturn("abc")
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))
        whenever(mockAppBuildConfig.versionName).thenReturn("name")
        whenever(mockAppBuildConfig.sdkInt).thenReturn(16)
        whenever(mockAppBuildConfig.manufacturer).thenReturn("Samsung")
        whenever(mockAppDaysUsedRepository.getLastActiveDay()).thenReturn("today")

        val jsonMessageAction = JsonMessageAction(
            type = "survey",
            value = "http://example.com",
            additionalParameters = mapOf("queryParams" to "atb;var;delta;av;ddgv;man;mo;da;src"),
        )
        val action = testee.evaluate(jsonMessageAction) as Action.Survey

        val urlWithParams = action.value.toUri()
        assertEquals("123", urlWithParams.getQueryParameter("atb"))
        assertEquals("abc", urlWithParams.getQueryParameter("var"))
        assertEquals("2", urlWithParams.getQueryParameter("delta"))
        assertEquals("16", urlWithParams.getQueryParameter("av"))
        assertEquals("name", urlWithParams.getQueryParameter("ddgv"))
        assertEquals("Samsung", urlWithParams.getQueryParameter("man"))
        assertEquals("in_app", urlWithParams.getQueryParameter("src"))
        assertEquals("today", urlWithParams.getQueryParameter("da"))
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
