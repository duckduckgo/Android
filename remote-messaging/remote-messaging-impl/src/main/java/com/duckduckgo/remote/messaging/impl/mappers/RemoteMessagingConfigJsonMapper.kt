/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl.mappers

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.impl.models.JsonActionType.APP_TP_ONBOARDING
import com.duckduckgo.remote.messaging.impl.models.JsonActionType.DEFAULT_BROWSER
import com.duckduckgo.remote.messaging.impl.models.JsonActionType.DISMISS
import com.duckduckgo.remote.messaging.impl.models.JsonActionType.PLAYSTORE
import com.duckduckgo.remote.messaging.impl.models.JsonActionType.URL
import com.duckduckgo.remote.messaging.impl.models.JsonMessageAction
import com.duckduckgo.remote.messaging.impl.models.JsonRemoteMessagingConfig
import com.duckduckgo.remote.messaging.impl.models.RemoteConfig
import timber.log.Timber

class RemoteMessagingConfigJsonMapper(
    private val appBuildConfig: AppBuildConfig
) {
    fun map(jsonRemoteMessagingConfig: JsonRemoteMessagingConfig): RemoteConfig {

        val actionMappers = listOf<ActionMapper>(
            ActionMapper(urlActionMapper),
            ActionMapper(dismissActionMapper),
            ActionMapper(playStoreActionMapper),
            ActionMapper(appTpOnboardingActionMapper),
        )
        val messages = jsonRemoteMessagingConfig.messages.mapToRemoteMessage(appBuildConfig.deviceLocale, actionMappers)
        Timber.i("RMF: messages parsed $messages")
        val rules = jsonRemoteMessagingConfig.rules.mapToMatchingRules()
        return RemoteConfig(
            messages = messages,
            rules = rules,
        )
    }
}

private val urlActionMapper: (JsonMessageAction) -> Action? = {
    if (it.type == URL.jsonValue) {
        Action.Url(it.value)
    } else {
        null
    }
}

private val dismissActionMapper: (JsonMessageAction) -> Action? = {
    if (it.type == DISMISS.jsonValue) {
        Action.Dismiss()
    } else {
        null
    }
}

private val playStoreActionMapper: (JsonMessageAction) -> Action? = {
    if (it.type == PLAYSTORE.jsonValue) {
        Action.PlayStore(it.value)
    } else {
        null
    }
}

private val defaultBrowserActionMapper: (JsonMessageAction) -> Action? = {
    if (it.type == DEFAULT_BROWSER.jsonValue) {
        Action.DefaultBrowser()
    } else {
        null
    }
}

private val appTpOnboardingActionMapper: (JsonMessageAction) -> Action? = {
    if (it.type == APP_TP_ONBOARDING.jsonValue) {
        Action.AppTpOnboarding()
    } else {
        null
    }
}

data class ActionMapper(
    val mapper: (JsonMessageAction) -> Action?
)
