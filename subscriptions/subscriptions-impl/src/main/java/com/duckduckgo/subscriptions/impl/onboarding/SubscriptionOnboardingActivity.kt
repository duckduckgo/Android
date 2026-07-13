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

package com.duckduckgo.subscriptions.impl.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.SettingsScreenNoParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepHost
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepPlugin
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionOnboardingScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.databinding.ActivitySubscriptionOnboardingBinding
import com.duckduckgo.subscriptions.impl.onboarding.SubscriptionOnboardingViewModel.Command
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Hosts the native subscription onboarding flow. It observes the orchestrator-driven state exposed by
 * [SubscriptionOnboardingViewModel] and swaps the current step's Fragment (supplied by the step's
 * [SubscriptionOnboardingStepPlugin], which may live in another module) into the container. Step
 * Fragments report back through [SubscriptionOnboardingStepHost].
 */
@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SubscriptionOnboardingScreenWithEmptyParams::class, screenName = "subscriptions.onboarding")
class SubscriptionOnboardingActivity : DuckDuckGoActivity(), SubscriptionOnboardingStepHost {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val viewModel: SubscriptionOnboardingViewModel by bindViewModel()
    private val binding: ActivitySubscriptionOnboardingBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        // Route the system back gesture (and the toolbar up arrow, via onOptionsItemSelected) through the ViewModel.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.onBack()
                }
            },
        )

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)

        viewModel.start()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStepFinished(stepId: String, outcome: SubscriptionOnboardingStepOutcome) {
        viewModel.onStepFinished(stepId, outcome)
    }

    override fun exitOnboarding() {
        viewModel.onExit()
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.ShowStep -> showStep(command.stepPlugin)
            is Command.FinishToSettings -> finishToSettings()
            is Command.Finish -> finish()
        }
    }

    private fun showStep(stepPlugin: SubscriptionOnboardingStepPlugin) {
        supportActionBar?.setTitle(stepPlugin.titleResId)
        supportFragmentManager.commit {
            replace(binding.subscriptionOnboardingContainer.id, stepPlugin.createFragment(), stepPlugin.stepId)
        }
    }

    private fun finishToSettings() {
        globalActivityStarter.startIntent(this, SettingsScreenNoParams)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
        finish()
    }
}
