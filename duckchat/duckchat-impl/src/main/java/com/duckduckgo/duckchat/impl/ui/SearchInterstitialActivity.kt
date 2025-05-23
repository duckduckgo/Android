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

package com.duckduckgo.duckchat.impl.ui

import android.os.Bundle
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.extensions.showKeyboard
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.databinding.ActivitySearchInterstitialBinding
import com.duckduckgo.navigation.api.GlobalActivityStarter
import javax.inject.Inject

object SearchInterstitialActivityParams : GlobalActivityStarter.ActivityParams

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SearchInterstitialActivityParams::class)
class SearchInterstitialActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var appBrowserNav: BrowserNav

    @Inject
    lateinit var duckChat: DuckChat

    private val binding: ActivitySearchInterstitialBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.duckChatOmnibar.apply {
            selectTab(0)
            enableFireButton = false
            enableNewChatButton = false
            onSearchSent = { query ->
                startActivity(appBrowserNav.openInCurrentTab(context, query))
                finish()
            }
            onDuckChatSent = { query ->
                duckChat.openDuckChatWithAutoPrompt(query)
                finish()
            }
            onBack = { onBackPressed() }
        }
        binding.duckChatOmnibar.duckChatInput.post {
            showKeyboard(binding.duckChatOmnibar.duckChatInput)
        }
    }
}
