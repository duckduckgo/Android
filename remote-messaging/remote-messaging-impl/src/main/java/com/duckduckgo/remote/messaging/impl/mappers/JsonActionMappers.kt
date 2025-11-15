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

package com.duckduckgo.remote.messaging.impl.mappers

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.DeeplinkActivityParams
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.JsonActionType
import com.duckduckgo.remote.messaging.api.JsonActionType.DEFAULT_BROWSER
import com.duckduckgo.remote.messaging.api.JsonActionType.DISMISS
import com.duckduckgo.remote.messaging.api.JsonActionType.NAVIGATION
import com.duckduckgo.remote.messaging.api.JsonActionType.PLAYSTORE
import com.duckduckgo.remote.messaging.api.JsonActionType.SHARE
import com.duckduckgo.remote.messaging.api.JsonActionType.URL
import com.duckduckgo.remote.messaging.api.JsonMessageAction
import com.duckduckgo.remote.messaging.api.MessageActionMapperPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.*

@ContributesMultibinding(
    AppScope::class,
)
class UrlActionMapper @Inject constructor() : MessageActionMapperPlugin {
    override fun evaluate(jsonMessageAction: JsonMessageAction): Action? {
        return if (jsonMessageAction.type == URL.jsonValue) {
            Action.Url(jsonMessageAction.value)
        } else {
            null
        }
    }
}

@ContributesMultibinding(
    AppScope::class,
)
class UrlInContextActionMapper @Inject constructor() : MessageActionMapperPlugin {
    override fun evaluate(jsonMessageAction: JsonMessageAction): Action? {
        return if (jsonMessageAction.type == JsonActionType.URL_IN_CONTEXT.jsonValue) {
            Action.UrlInContext(jsonMessageAction.value)
        } else {
            null
        }
    }
}

@ContributesMultibinding(
    AppScope::class,
)
class DismissActionMapper @Inject constructor() : MessageActionMapperPlugin {
    override fun evaluate(jsonMessageAction: JsonMessageAction): Action? {
        return if (jsonMessageAction.type == DISMISS.jsonValue) {
            Action.Dismiss
        } else {
            null
        }
    }
}

@ContributesMultibinding(
    AppScope::class,
)
class PlayStoreActionMapper @Inject constructor() : MessageActionMapperPlugin {
    override fun evaluate(jsonMessageAction: JsonMessageAction): Action? {
        return if (jsonMessageAction.type == PLAYSTORE.jsonValue) {
            Action.PlayStore(jsonMessageAction.value)
        } else {
            null
        }
    }
}

@ContributesMultibinding(
    AppScope::class,
)
class DefaultBrowserActionMapper @Inject constructor() : MessageActionMapperPlugin {
    override fun evaluate(jsonMessageAction: JsonMessageAction): Action? {
        return if (jsonMessageAction.type == DEFAULT_BROWSER.jsonValue) {
            Action.DefaultBrowser
        } else {
            null
        }
    }
}

@ContributesMultibinding(
    AppScope::class,
)
class ShareActionMapper @Inject constructor() : MessageActionMapperPlugin {
    override fun evaluate(jsonMessageAction: JsonMessageAction): Action? {
        return if (jsonMessageAction.type == SHARE.jsonValue) {
            Action.Share(jsonMessageAction.value, jsonMessageAction.additionalParameters)
        } else {
            null
        }
    }
}

@ContributesMultibinding(
    AppScope::class,
)
class NavigationActionMapper @Inject constructor(
    val context: Context,
    private val globalActivityStarter: GlobalActivityStarter,
) : MessageActionMapperPlugin {
    override fun evaluate(jsonMessageAction: JsonMessageAction): Action? {
        if (jsonMessageAction.type == NAVIGATION.jsonValue) {
            val payload = jsonMessageAction.additionalParameters?.get("payload") ?: ""
            globalActivityStarter.startIntent(context, DeeplinkActivityParams(jsonMessageAction.value, payload))?.let {
                return Action.Navigation(jsonMessageAction.value, jsonMessageAction.additionalParameters)
            }
        }
        return null
    }
}
