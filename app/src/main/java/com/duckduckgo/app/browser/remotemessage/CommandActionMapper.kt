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

package com.duckduckgo.app.browser.remotemessage

import com.duckduckgo.app.browser.newtab.NewTabPageViewModel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.Action.*
import com.duckduckgo.survey.api.SurveyParameterManager
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface CommandActionMapper {
    suspend fun asNewTabCommand(action: Action): NewTabPageViewModel.Command
}

@ContributesBinding(ActivityScope::class)
class RealCommandActionMapper @Inject constructor(
    private val surveyParameterManager: SurveyParameterManager,
) : CommandActionMapper {
    override suspend fun asNewTabCommand(action: Action): NewTabPageViewModel.Command {
        return when (action) {
            is Dismiss -> NewTabPageViewModel.Command.DismissMessage
            is PlayStore -> NewTabPageViewModel.Command.LaunchPlayStore(action.value)
            is Url -> NewTabPageViewModel.Command.SubmitUrl(action.value)
            is UrlInContext -> NewTabPageViewModel.Command.SubmitUrl(action.value)
            is DefaultBrowser -> NewTabPageViewModel.Command.LaunchDefaultBrowser
            is AppTpOnboarding -> NewTabPageViewModel.Command.LaunchAppTPOnboarding
            is Share -> NewTabPageViewModel.Command.SharePromoLinkRMF(action.value, action.title)
            is Navigation -> { NewTabPageViewModel.Command.LaunchScreen(action.value, action.additionalParameters?.get("payload").orEmpty()) }
            is Survey -> {
                val queryParams = action.additionalParameters?.get("queryParams")?.split(";") ?: emptyList()
                NewTabPageViewModel.Command.SubmitUrl(surveyParameterManager.buildSurveyUrl(action.value, queryParams))
            }
            is DefaultCredentialProvider -> NewTabPageViewModel.Command.LaunchDefaultCredentialProvider
        }
    }
}
