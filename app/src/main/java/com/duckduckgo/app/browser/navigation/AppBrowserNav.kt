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
import com.duckduckgo.app.browser.mode.BrowserLaunchSource
import com.duckduckgo.app.browser.mode.DuckAiWidget
import com.duckduckgo.app.browser.mode.InAppNavigation
import com.duckduckgo.app.browser.mode.SelectedTextSearch
import com.duckduckgo.app.browser.nativeinput.NativeInputLaunch
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.app.tabs.DuckChatLaunch
import com.duckduckgo.app.tabs.DuckChatLaunchOrigin
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AppBrowserNav @Inject constructor() : BrowserNav {
    override fun openInNewTab(
        context: Context,
        url: String,
        sourceTabId: String?,
    ): Intent {
        return BrowserActivity.intent(
            context = context,
            launchSource = InAppNavigation,
            queryExtra = url,
            interstitialScreen = true,
            sourceTabId = sourceTabId,
        )
    }

    override fun openInCurrentTab(
        context: Context,
        url: String,
    ): Intent {
        return BrowserActivity.intent(context = context, launchSource = InAppNavigation, queryExtra = url, openInCurrentTab = true)
    }

    override fun openDuckChat(
        context: Context,
        hasSessionActive: Boolean,
        duckChatUrl: String?,
        launch: DuckChatLaunch,
    ): Intent {
        val contextual = launch as? DuckChatLaunch.ContextualChat
        val externalSelection = launch as? DuckChatLaunch.TextSelection
        val launchSource = when (launch) {
            is DuckChatLaunch.Open -> launch.origin.toLaunchSource()
            is DuckChatLaunch.ImageGeneration -> launch.origin.toLaunchSource()
            is DuckChatLaunch.CameraCapture -> launch.origin.toLaunchSource()
            is DuckChatLaunch.Voice -> launch.origin.toLaunchSource()
            is DuckChatLaunch.ChatHistory -> launch.origin.toLaunchSource()
            is DuckChatLaunch.ContextualChat -> InAppNavigation
            is DuckChatLaunch.TextSelection -> SelectedTextSearch
        }
        return BrowserActivity.intent(
            context = context,
            launchSource = launchSource,
            openDuckChat = launch !is DuckChatLaunch.ChatHistory,
            duckChatContextual = contextual != null,
            duckChatUrl = duckChatUrl,
            duckChatSessionActive = hasSessionActive,
            duckChatForceImageGeneration = launch is DuckChatLaunch.ImageGeneration,
            duckChatVoice = launch is DuckChatLaunch.Voice,
            openDuckChatHistory = launch is DuckChatLaunch.ChatHistory,
            duckChatTextSelection = contextual?.textSelection ?: externalSelection?.text,
            nativeInputLaunch = NativeInputLaunch.from(launch),
        )
    }

    private fun DuckChatLaunchOrigin.toLaunchSource(): BrowserLaunchSource = when (this) {
        DuckChatLaunchOrigin.IN_APP -> InAppNavigation
        DuckChatLaunchOrigin.HOME_SCREEN_WIDGET -> DuckAiWidget
    }

    override fun closeDuckChat(
        context: Context,
    ): Intent {
        return BrowserActivity.intent(context = context, launchSource = InAppNavigation, closeDuckChat = true)
    }

    override fun openExistingTab(
        context: Context,
        tabId: String,
    ): Intent {
        return BrowserActivity.intent(context = context, launchSource = InAppNavigation, openExistingTabId = tabId)
    }
}
