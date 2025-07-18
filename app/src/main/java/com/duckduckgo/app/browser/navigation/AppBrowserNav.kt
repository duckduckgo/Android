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

package com.duckduckgo.app.browser.navigation

import android.content.Context
import android.content.Intent
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class AppBrowserNav @Inject constructor() : BrowserNav {
    override fun openInNewTab(
        context: Context,
        url: String,
    ): Intent {
        return BrowserActivity.intent(context = context, queryExtra = url, interstitialScreen = true)
    }

    override fun openInCurrentTab(
        context: Context,
        url: String,
    ): Intent {
        return BrowserActivity.intent(context = context, queryExtra = url, openInCurrentTab = true)
    }

    override fun openDuckChat(
        context: Context,
        hasSessionActive: Boolean,
        duckChatUrl: String,
    ): Intent {
        return BrowserActivity.intent(context = context, openDuckChat = true, duckChatUrl = duckChatUrl, duckChatSessionActive = hasSessionActive)
    }

    override fun closeDuckChat(
        context: Context,
    ): Intent {
        return BrowserActivity.intent(context = context, closeDuckChat = true)
    }
}
