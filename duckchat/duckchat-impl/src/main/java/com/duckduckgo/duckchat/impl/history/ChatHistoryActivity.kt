/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.history

import android.os.Bundle
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChatHistoryParams
import com.duckduckgo.duckchat.impl.databinding.ActivityChatHistoryBinding
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import javax.inject.Inject

/**
 * Thin host for chat-history-related fragments.
 */
@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(DuckChatHistoryDeeplinkParams::class, screenName = "duckai.history")
@ContributeToActivityStarter(DuckChatHistoryParams::class)
class ChatHistoryActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var duckChatPixels: DuckChatPixels

    private val binding: ActivityChatHistoryBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)

        if (savedInstanceState == null) {
            reportScreenShown()
            supportFragmentManager.beginTransaction()
                .replace(binding.chatHistoryFragmentContainer.id, ChatHistoryFragment.newInstance())
                .commit()
        }
    }

    private fun reportScreenShown() {
        when (val params = intent.getActivityParams(GlobalActivityStarter.ActivityParams::class.java)) {
            is DuckChatHistoryParams -> duckChatPixels.reportChatHistoryScreenShown(params.source)
            is DuckChatHistoryDeeplinkParams -> duckChatPixels.reportChatHistoryScreenShownFromDeeplink()
            else -> Unit
        }
    }
}

internal data object DuckChatHistoryDeeplinkParams : GlobalActivityStarter.ActivityParams
