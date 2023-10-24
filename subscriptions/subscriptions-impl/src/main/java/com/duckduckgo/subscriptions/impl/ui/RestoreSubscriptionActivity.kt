/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.ui

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.impl.R.string
import com.duckduckgo.subscriptions.impl.databinding.ActivityRestoreSubscriptionBinding
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionActivity.Companion.RestoreSubscriptionScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.Error
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.RestoreFromEmail
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.SubscriptionNotFound
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionViewModel.Command.Success
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsActivity.Companion.SubscriptionsSettingsScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsActivity.Companion.SubscriptionsScreenWithEmptyParams
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(RestoreSubscriptionScreenWithEmptyParams::class)
class RestoreSubscriptionActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val viewModel: RestoreSubscriptionViewModel by bindViewModel()
    private val binding: ActivityRestoreSubscriptionBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(toolbar)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)

        binding.googlePlay.setPrimaryButtonClickListener {
            viewModel.restoreFromStore()
        }

        binding.email.setPrimaryButtonClickListener {
            viewModel.restoreFromEmail()
        }
    }

    private fun goToAddEmail() {
        globalActivityStarter.start(
            this,
            SubscriptionsWebViewActivityWithParams(
                url = "https://abrown.duckduckgo.com/subscriptions/activate",
                getString(string.addEmailText),
            ),
        )
    }

    private fun onPurchaseRestored() {
        TextAlertDialogBuilder(this)
            .setTitle(string.youAreSet)
            .setMessage(string.purchaseRestored)
            .setPositiveButton(string.ok)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        globalActivityStarter.start(this@RestoreSubscriptionActivity, SubscriptionsSettingsScreenWithEmptyParams)
                        finish()
                    }
                },
            )
            .show()
    }

    private fun subscriptionNotFound() {
        TextAlertDialogBuilder(this)
            .setTitle(string.subscriptionNotFound)
            .setMessage(string.subscriptionNotFoundDescription)
            .setDestructiveButtons(false)
            .setPositiveButton(string.viewPlans)
            .setNegativeButton(string.cancel)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        globalActivityStarter.start(this@RestoreSubscriptionActivity, SubscriptionsScreenWithEmptyParams)
                    }
                },
            )
            .show()
    }

    private fun showError(message: String) {
        TextAlertDialogBuilder(this)
            .setTitle(string.somethingWentWrong)
            .setMessage(message)
            .setDestructiveButtons(false)
            .setPositiveButton(string.ok)
            .show()
    }

    private fun processCommand(command: Command) {
        when (command) {
            is RestoreFromEmail -> goToAddEmail()
            is Success -> onPurchaseRestored()
            is SubscriptionNotFound -> subscriptionNotFound()
            is Error -> showError(command.message)
        }
    }
    companion object {
        object RestoreSubscriptionScreenWithEmptyParams : GlobalActivityStarter.ActivityParams
    }
}
