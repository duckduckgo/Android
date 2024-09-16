/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.onboarding

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.JsonActionType.APP_TP_ONBOARDING
import com.duckduckgo.remote.messaging.api.JsonMessageAction
import com.duckduckgo.remote.messaging.api.MessageActionMapperPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.*

@ContributesMultibinding(
    AppScope::class,
    boundType = MessageActionMapperPlugin::class,
)
class AppTPRemoteMessageNavigationAction @Inject constructor() : MessageActionMapperPlugin {

    override fun evaluate(jsonMessageAction: JsonMessageAction): Action? {
        val isAppTPOnboarding = jsonMessageAction.type == APP_TP_ONBOARDING.jsonValue
        return if (isAppTPOnboarding) {
            Action.AppTpOnboarding
        } else {
            null
        }
    }
}
