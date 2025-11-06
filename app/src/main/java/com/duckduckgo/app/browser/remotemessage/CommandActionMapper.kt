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

import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.Action.*
import com.duckduckgo.survey.api.SurveyParameterManager
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface CommandActionMapper {
    suspend fun asNewTabCommand(action: Action): NewTabLegacyPageViewModel.Command
}

@ContributesBinding(ActivityScope::class)
class RealCommandActionMapper @Inject constructor(
    private val surveyParameterManager: SurveyParameterManager,
) : CommandActionMapper {
    override suspend fun asNewTabCommand(action: Action): NewTabLegacyPageViewModel.Command {
        return when (action) {
            is Dismiss -> NewTabLegacyPageViewModel.Command.DismissMessage
            is PlayStore -> NewTabLegacyPageViewModel.Command.LaunchPlayStore(action.value)
            is Url -> NewTabLegacyPageViewModel.Command.SubmitUrl(action.value)
            is UrlInContext -> NewTabLegacyPageViewModel.Command.SubmitUrlInContext(action.value)
            is DefaultBrowser -> NewTabLegacyPageViewModel.Command.LaunchDefaultBrowser
            is AppTpOnboarding -> NewTabLegacyPageViewModel.Command.LaunchAppTPOnboarding
            is Share -> NewTabLegacyPageViewModel.Command.SharePromoLinkRMF(action.value, action.title)
            is Navigation -> { NewTabLegacyPageViewModel.Command.LaunchScreen(action.value, action.additionalParameters?.get("payload").orEmpty()) }
            is Survey -> {
                val queryParams = action.additionalParameters?.get("queryParams")?.split(";") ?: emptyList()
                NewTabLegacyPageViewModel.Command.SubmitUrl(surveyParameterManager.buildSurveyUrl(action.value, queryParams))
            }
        }
    }
}
