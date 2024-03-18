/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.launch

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.onboarding.ui.OnboardingActivity
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.launch

@InjectWith(ActivityScope::class)
class LaunchBridgeActivity : DuckDuckGoActivity() {

    private val viewModel: LaunchViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch)

        configureObservers()

        lifecycleScope.launch { viewModel.determineViewToShow() }
    }

    private fun configureObservers() {
        viewModel.command.observe(this) {
            processCommand(it)
        }
    }

    private fun processCommand(it: LaunchViewModel.Command) {
        when (it) {
            is LaunchViewModel.Command.Onboarding -> {
                showOnboarding(it.forceLightTheme)
            }
            is LaunchViewModel.Command.Home -> {
                showHome()
            }
        }
    }

    private fun showOnboarding(forceLightTheme: Boolean) {
        startActivity(OnboardingActivity.intent(this, forceLightTheme))
        finish()
    }

    private fun showHome() {
        startActivity(BrowserActivity.intent(this))
        overridePendingTransition(0, 0)
        finish()
    }
}
