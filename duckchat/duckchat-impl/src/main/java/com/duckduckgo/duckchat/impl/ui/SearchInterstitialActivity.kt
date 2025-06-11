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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.common.utils.extensions.showKeyboard
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.databinding.ActivitySearchInterstitialBinding
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import javax.inject.Inject

data class SearchInterstitialActivityParams(
    val query: String,
) : GlobalActivityStarter.ActivityParams

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SearchInterstitialActivityParams::class)
class SearchInterstitialActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var duckChat: DuckChat

    private val binding: ActivitySearchInterstitialBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val params = intent.getActivityParams(SearchInterstitialActivityParams::class.java)
        params?.query?.let { query ->
            binding.duckChatOmnibar.duckChatInput.setText(query)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val query = binding.duckChatOmnibar.duckChatInput.text.toString()
                    val data = Intent().putExtra(QUERY, query)
                    setResult(Activity.RESULT_CANCELED, data)
                    exitInterstitial()
                }
            },
        )

        binding.duckChatOmnibar.apply {
            selectTab(0)
            enableFireButton = false
            enableNewChatButton = false
            onSearchSent = { query ->
                val data = Intent().putExtra(QUERY, query)
                setResult(Activity.RESULT_OK, data)
                exitInterstitial()
            }
            onDuckChatSent = { query ->
                duckChat.openDuckChatWithAutoPrompt(query)
                finish()
            }
            onBack = {
                onBackPressed()
            }
        }
        binding.duckChatOmnibar.duckChatInput.post {
            showKeyboard(binding.duckChatOmnibar.duckChatInput)
        }
    }

    private fun exitInterstitial() {
        binding.duckChatOmnibar.animateOmnibarFocusedState(false)
        hideKeyboard(binding.duckChatOmnibar.duckChatInput)
        supportFinishAfterTransition()
    }

    companion object {
        // TODO: This is in an :impl module and accessed directly from :app module, it should be moved to an API
        const val QUERY = "query"
    }
}
