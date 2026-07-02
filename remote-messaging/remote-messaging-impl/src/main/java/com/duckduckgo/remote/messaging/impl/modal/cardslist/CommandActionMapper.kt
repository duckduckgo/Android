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

import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.Action.AppTpOnboarding
import com.duckduckgo.remote.messaging.api.Action.DefaultBrowser
import com.duckduckgo.remote.messaging.api.Action.DefaultCredentialProvider
import com.duckduckgo.remote.messaging.api.Action.Dismiss
import com.duckduckgo.remote.messaging.api.Action.Navigation
import com.duckduckgo.remote.messaging.api.Action.PlayStore
import com.duckduckgo.remote.messaging.api.Action.Share
import com.duckduckgo.remote.messaging.api.Action.Survey
import com.duckduckgo.remote.messaging.api.Action.Url
import com.duckduckgo.remote.messaging.api.Action.UrlInContext
import com.duckduckgo.remote.messaging.impl.modal.cardslist.CardsListRemoteMessageViewModel.Command
import com.duckduckgo.survey.api.SurveyParameterManager
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlin.text.orEmpty
import kotlin.text.split

interface CommandActionMapper {
    suspend fun asCommand(action: Action): Command
}

@ContributesBinding(ActivityScope::class)
class RealCommandActionMapper @Inject constructor(
    private val surveyParameterManager: SurveyParameterManager,
) : CommandActionMapper {
    override suspend fun asCommand(action: Action): Command {
        return when (action) {
            is Dismiss -> Command.DismissMessage
            is PlayStore -> Command.LaunchPlayStore(action.value)
            is Url -> Command.SubmitUrl(action.value)
            is UrlInContext -> Command.SubmitUrlInContext(action.value)
            is DefaultBrowser -> Command.LaunchDefaultBrowser
            is AppTpOnboarding -> Command.LaunchAppTPOnboarding
            is Share -> Command.SharePromoLinkRMF(action.value, action.title)
            is Navigation -> {
                Command.LaunchScreen(action.value, action.additionalParameters?.get("payload").orEmpty())
            }
            is Survey -> {
                val queryParams = action.additionalParameters?.get("queryParams")?.split(";") ?: emptyList()
                Command.SubmitUrl(surveyParameterManager.buildSurveyUrl(action.value, queryParams))
            }
            is DefaultCredentialProvider -> Command.LaunchDefaultCredentialProvider
        }
    }
}
