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

package com.duckduckgo.autofill.impl.ui.credential.management.survey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.ui.credential.management.survey.SurveyInPasswordsPromotionViewModel.Command.DismissSurvey
import com.duckduckgo.autofill.impl.ui.credential.management.survey.SurveyInPasswordsPromotionViewModel.Command.LaunchSurvey
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ViewScope::class)
class SurveyInPasswordsPromotionViewModel @Inject constructor(
    private val autofillSurvey: AutofillSurvey,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    sealed interface Command {
        data class LaunchSurvey(val surveyUrl: String) : Command
        data object DismissSurvey : Command
    }

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    // want to ensure this pixel doesn't trigger repeatedly as it's scrolled in and out of the list
    private var promoDisplayedPixelSent = false

    fun onPromoShown() {
        if (!promoDisplayedPixelSent) {
            promoDisplayedPixelSent = true
            pixel.fire(AutofillPixelNames.AUTOFILL_SURVEY_AVAILABLE_PROMPT_DISPLAYED)
        }
    }

    fun onUserChoseToOpenSurvey(survey: SurveyDetails) {
        viewModelScope.launch(dispatchers.io()) {
            autofillSurvey.recordSurveyAsUsed(survey.id)
            command.send(LaunchSurvey(survey.url))
        }
    }

    fun onSurveyPromptDismissed(surveyId: String) {
        viewModelScope.launch(dispatchers.io()) {
            autofillSurvey.recordSurveyAsUsed(surveyId)
            command.send(DismissSurvey)
        }
    }
}
