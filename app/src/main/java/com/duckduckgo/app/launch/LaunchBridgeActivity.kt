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
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.onboarding.ui.OnboardingActivity
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.daxprompts.api.DaxPromptBrowserComparisonNoParams
import com.duckduckgo.daxprompts.impl.ui.DaxPromptBrowserComparisonActivity.Companion.DAX_PROMPT_BROWSER_COMPARISON_SET_DEFAULT_EXTRA
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class LaunchBridgeActivity : DuckDuckGoActivity() {

    private val viewModel: LaunchViewModel by bindViewModel()

    private val startDaxPromptBrowserComparisonActivityForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                val show = result.data?.getBooleanExtra(DAX_PROMPT_BROWSER_COMPARISON_SET_DEFAULT_EXTRA, false)
                logcat { "Received RESULT_OK from DaxPromptBrowserComparisonActivity with extra: $show" }
                viewModel.onDaxPromptBrowserComparisonActivityResult(show)
            } else {
                logcat { "Received non-OK result from DaxPromptBrowserComparisonActivity" }
                viewModel.onDaxPromptBrowserComparisonActivityResult()
            }
        }

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { true }

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
                showOnboarding()
            }

            is LaunchViewModel.Command.Home -> {
                showHome()
            }

            is LaunchViewModel.Command.CloseDaxPrompt -> {
                lifecycleScope.launch { viewModel.showOnboardingOrHome() }
            }

            is LaunchViewModel.Command.DaxPromptBrowserComparison -> {
                showDaxPromptBrowserComparison()
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

    private fun showDaxPromptBrowserComparison() {
        val intentDaxPromptComparisonChart =
            globalActivityStarter.startIntent(this, DaxPromptBrowserComparisonNoParams)
        intentDaxPromptComparisonChart?.let {
            startDaxPromptBrowserComparisonActivityForResult.launch(it)
        }
    }
}
