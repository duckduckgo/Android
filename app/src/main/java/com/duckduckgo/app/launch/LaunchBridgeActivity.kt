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
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.onboarding.ui.OnboardingActivity
import com.duckduckgo.app.settings.SettingsActivity
import com.duckduckgo.app.statistics.VariantManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class LaunchBridgeActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var variantManager: VariantManager

    private val viewModel: LaunchViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch)

        configureObservers()

        MainScope().launch { viewModel.determineViewToShow() }
    }

    private fun configureObservers() {
        viewModel.command.observe(
            this,
            Observer {
                processCommand(it)
            }
        )
    }

    private fun processCommand(it: LaunchViewModel.Command?) {
        when (it) {
            LaunchViewModel.Command.Onboarding -> {
                showOnboarding()
            }
            is LaunchViewModel.Command.Home -> {
                showHome()
            }
        }
    }

    private fun showOnboarding() {
        startActivity(OnboardingActivity.intent(this))
        finish()
    }

    private fun showHome() {
        startActivity(BrowserActivity.intent(this))
        overridePendingTransition(0, 0)
        finish()
    }
}
