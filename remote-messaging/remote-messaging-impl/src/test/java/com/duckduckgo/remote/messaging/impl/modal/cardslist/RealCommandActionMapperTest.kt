/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl.modal.cardslist

import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.impl.modal.cardslist.CardsListRemoteMessageViewModel.Command
import com.duckduckgo.survey.api.SurveyParameterManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealCommandActionMapperTest {

    private lateinit var mapper: RealCommandActionMapper
    private val surveyParameterManager: SurveyParameterManager = mock()

    @Before
    fun setup() {
        mapper = RealCommandActionMapper(surveyParameterManager)
    }

    @Test
    fun whenDismissActionThenReturnsDismissMessageCommand() = runTest {
        val action = Action.Dismiss

        val result = mapper.asCommand(action)

        assertTrue(result is Command.DismissMessage)
    }

    @Test
    fun whenPlayStoreActionThenReturnsLaunchPlayStoreCommand() = runTest {
        val appPackage = "com.example.app"
        val action = Action.PlayStore(appPackage)

        val result = mapper.asCommand(action)

        assertTrue(result is Command.LaunchPlayStore)
        assertEquals(appPackage, (result as Command.LaunchPlayStore).appPackage)
    }

    @Test
    fun whenUrlActionThenReturnsSubmitUrlCommand() = runTest {
        val url = "https://example.com"
        val action = Action.Url(url)

        val result = mapper.asCommand(action)

        assertTrue(result is Command.SubmitUrl)
        assertEquals(url, (result as Command.SubmitUrl).url)
    }

    @Test
    fun whenUrlInContextActionThenReturnsSubmitUrlInContextCommand() = runTest {
        val url = "https://example.com"
        val action = Action.UrlInContext(url)

        val result = mapper.asCommand(action)

        assertTrue(result is Command.SubmitUrlInContext)
        assertEquals(url, (result as Command.SubmitUrlInContext).url)
    }

    @Test
    fun whenDefaultBrowserActionThenReturnsLaunchDefaultBrowserCommand() = runTest {
        val action = Action.DefaultBrowser

        val result = mapper.asCommand(action)

        assertTrue(result is Command.LaunchDefaultBrowser)
    }

    @Test
    fun whenAppTpOnboardingActionThenReturnsLaunchAppTPOnboardingCommand() = runTest {
        val action = Action.AppTpOnboarding

        val result = mapper.asCommand(action)

        assertTrue(result is Command.LaunchAppTPOnboarding)
    }

    @Test
    fun whenShareActionThenReturnsSharePromoLinkCommand() = runTest {
        val url = "https://share.example.com"
        val title = "Check this out"
        val action = Action.Share(url, mapOf("title" to title))

        val result = mapper.asCommand(action)

        assertTrue(result is Command.SharePromoLinkRMF)
        assertEquals(url, (result as Command.SharePromoLinkRMF).url)
        assertEquals(title, result.shareTitle)
    }

    @Test
    fun whenNavigationActionWithoutPayloadThenReturnsLaunchScreenCommandWithEmptyPayload() = runTest {
        val screenName = "settings"
        val action = Action.Navigation(screenName, null)

        val result = mapper.asCommand(action)

        assertTrue(result is Command.LaunchScreen)
        assertEquals(screenName, (result as Command.LaunchScreen).screen)
        assertEquals("", result.payload)
    }

    @Test
    fun whenNavigationActionWithPayloadThenReturnsLaunchScreenCommandWithPayload() = runTest {
        val screenName = "settings"
        val payload = "feature_x"
        val action = Action.Navigation(screenName, mapOf("payload" to payload))

        val result = mapper.asCommand(action)

        assertTrue(result is Command.LaunchScreen)
        assertEquals(screenName, (result as Command.LaunchScreen).screen)
        assertEquals(payload, result.payload)
    }

    @Test
    fun whenSurveyActionWithoutQueryParamsThenReturnsSubmitUrlCommandWithBuiltSurveyUrl() = runTest {
        val surveyId = "survey-123"
        val builtUrl = "https://survey.example.com?id=survey-123"
        whenever(surveyParameterManager.buildSurveyUrl(eq(surveyId), eq(emptyList()))).thenReturn(builtUrl)
        val action = Action.Survey(surveyId, null)

        val result = mapper.asCommand(action)

        assertTrue(result is Command.SubmitUrl)
        assertEquals(builtUrl, (result as Command.SubmitUrl).url)
    }

    @Test
    fun whenSurveyActionWithQueryParamsThenReturnsSubmitUrlCommandWithParameterizedSurveyUrl() = runTest {
        val surveyId = "survey-123"
        val queryParams = "param1=value1;param2=value2"
        val expectedParams = listOf("param1=value1", "param2=value2")
        val builtUrl = "https://survey.example.com?id=survey-123&param1=value1&param2=value2"
        whenever(surveyParameterManager.buildSurveyUrl(eq(surveyId), eq(expectedParams))).thenReturn(builtUrl)
        val action = Action.Survey(surveyId, mapOf("queryParams" to queryParams))

        val result = mapper.asCommand(action)

        assertTrue(result is Command.SubmitUrl)
        assertEquals(builtUrl, (result as Command.SubmitUrl).url)
    }

    @Test
    fun whenSurveyActionWithEmptyQueryParamsThenReturnsSubmitUrlCommandWithEmptyParamsList() = runTest {
        val surveyId = "survey-123"
        val builtUrl = "https://survey.example.com?id=survey-123"
        val action = Action.Survey(surveyId, mapOf("queryParams" to ""))
        whenever(surveyParameterManager.buildSurveyUrl(eq(surveyId), any())).thenReturn(builtUrl)

        val result = mapper.asCommand(action)

        assertTrue(result is Command.SubmitUrl)
        assertEquals(builtUrl, (result as Command.SubmitUrl).url)
    }

    @Test
    fun whenDefaultCredentialProviderActionThenReturnsLaunchDefaultCredentialProviderCommand() = runTest {
        val action = Action.DefaultCredentialProvider

        val result = mapper.asCommand(action)

        assertTrue(result is Command.LaunchDefaultCredentialProvider)
    }
}
