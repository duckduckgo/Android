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

package com.duckduckgo.app.browser.messaging

import com.duckduckgo.browser.api.WebMessagingBrowserPlugin
import com.duckduckgo.contentscopescripts.impl.messaging.ContentScopeScriptsWebMessaging
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.js.messaging.api.WebMessaging
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import javax.inject.Named

@Named("contentScopeScripts")
@SingleInstanceIn(FragmentScope::class)
@ContributesBinding(FragmentScope::class)
@ContributesMultibinding(scope = FragmentScope::class, ignoreQualifier = true)
class ContentScopeScriptsWebMessagingBrowserPlugin @Inject constructor(
    private val contentScopeScriptsWebMessaging: ContentScopeScriptsWebMessaging,
) : WebMessagingBrowserPlugin {
    override fun webMessaging(): WebMessaging {
        return contentScopeScriptsWebMessaging
    }
}
