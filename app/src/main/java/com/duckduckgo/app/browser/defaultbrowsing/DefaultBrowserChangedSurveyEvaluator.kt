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

import android.content.Context
import android.content.Intent
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.ui.SurveyActivity
import com.duckduckgo.app.survey.ui.SurveyActivity.Companion.SurveySource
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.modalcoordinator.api.ModalEvaluator
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

interface DefaultBrowserChangedSurveyEvaluator

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ModalEvaluator::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = DefaultBrowserChangedSurveyEvaluator::class,
)
@SingleInstanceIn(scope = AppScope::class)
class DefaultBrowserChangedSurveyEvaluatorImpl @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val applicationContext: Context,
    private val surveyManager: DefaultBrowserChangedSurveyManager,
    private val dispatchers: DispatcherProvider,
) : ModalEvaluator, DefaultBrowserChangedSurveyEvaluator {

    override val priority: Int = 1

    override val evaluatorId: String = EVALUATOR_ID

    override suspend fun evaluate(): ModalEvaluator.EvaluationResult = withContext(dispatchers.io()) {
        if (!surveyManager.shouldTriggerSurvey()) {
            logcat(tag = "RadoiuC") { "Should not trigger survey from default browser changed evaluator" }
            return@withContext ModalEvaluator.EvaluationResult.Skipped
        }

        val survey = Survey(
            surveyId = DefaultBrowserChangedSurveyManager.SURVEY_ID_IN_APP,
            url = surveyManager.buildSurveyUrl(SURVEY_CHANNEL),
            daysInstalled = null,
            status = Survey.Status.SCHEDULED,
        )
        val intent = SurveyActivity.intent(applicationContext, survey, SurveySource.IN_APP).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        surveyManager.markSurveyShown()

        delay(MODAL_DISPLAY_DELAY)
        appCoroutineScope.launch(dispatchers.main()) {
            applicationContext.startActivity(intent)
        }

        return@withContext ModalEvaluator.EvaluationResult.ModalShown
    }

    companion object {
        const val EVALUATOR_ID = "default_browser_changed_survey_evaluator"
        private const val MODAL_DISPLAY_DELAY = 250L
        private const val SURVEY_CHANNEL = "in-app"
    }
}
