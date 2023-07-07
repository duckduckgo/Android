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

package com.duckduckgo.remote.messaging.fixtures

import android.content.Intent
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.JsonMessageAction
import com.duckduckgo.remote.messaging.api.MessageActionMapperPlugin
import com.duckduckgo.remote.messaging.impl.mappers.DefaultBrowserActionMapper
import com.duckduckgo.remote.messaging.impl.mappers.DismissActionMapper
import com.duckduckgo.remote.messaging.impl.mappers.PlayStoreActionMapper
import com.duckduckgo.remote.messaging.impl.mappers.UrlActionMapper

val messageActionPlugins = listOf(
    FakeAppNavigationMapper(),
    UrlActionMapper(),
    DismissActionMapper(),
    PlayStoreActionMapper(),
    DefaultBrowserActionMapper(),
).toSet()

class FakeAppNavigationMapper : MessageActionMapperPlugin {
    override fun evaluate(jsonMessageAction: JsonMessageAction): Action? {
        if (jsonMessageAction.type != "appNavigation") return null
        return Action.AppNavigation(Intent(), jsonMessageAction.value)
    }
}
