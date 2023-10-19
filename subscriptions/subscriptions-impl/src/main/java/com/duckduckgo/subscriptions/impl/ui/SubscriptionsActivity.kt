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
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.hide
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.impl.R.id
import com.duckduckgo.subscriptions.impl.R.string
import com.duckduckgo.subscriptions.impl.billing.getPrice
import com.duckduckgo.subscriptions.impl.databinding.ActivitySubscriptionsBinding
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionActivity.Companion.RestoreSubscriptionScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsActivity.Companion.SubscriptionsSettingsScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsActivity.Companion.SubscriptionsScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsViewModel.Command
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsViewModel.Command.ErrorMessage
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsViewModel.PurchaseStateView
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsViewModel.ViewState
import javax.inject.Inject
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SubscriptionsScreenWithEmptyParams::class)
class SubscriptionsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val viewModel: SubscriptionsViewModel by bindViewModel()
    private val binding: ActivitySubscriptionsBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.start()
        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach {
            if (it.subscriptionDetails != null) {
                renderProducts(it)
            }
            if (it.hasSubscription == true) {
                renderSubscribed()
            } else {
                renderNotSubscribed()
            }
        }.launchIn(lifecycleScope)

        viewModel.currentPurchaseViewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach {
            renderPurchase(it.purchaseState)
        }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)

        setContentView(binding.root)
        setupToolbar(toolbar)

        binding.recoverSubscriptionButton.setOnClickListener {
            globalActivityStarter.start(this, RestoreSubscriptionScreenWithEmptyParams)
        }
    }

    private fun renderPurchase(purchaseState: PurchaseStateView) {
        when (purchaseState) {
            is PurchaseStateView.InProgress -> {
                binding.container.gone()
                binding.progress.show()
            }
            is PurchaseStateView.Inactive -> {
                binding.container.show()
                binding.progress.gone()
            }
            is PurchaseStateView.Success -> {
                binding.container.show()
                binding.progress.gone()
                onPurchaseSuccess()
            }
            is PurchaseStateView.Recovered -> {
                binding.container.show()
                binding.progress.gone()
                onPurchaseRecovered()
            }
            is PurchaseStateView.Failure -> {
                binding.container.show()
                binding.progress.gone()
                onPurchaseFailure(purchaseState.message)
            }
        }
    }

    private fun processCommand(command: Command) {
        if (command is ErrorMessage) {
            Toast.makeText(this, command.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun renderNotSubscribed() {
        binding.purchaseDetails.text = "You are not subscribed yet"
        binding.recoverSubscriptionButton.show()
    }
    private fun renderSubscribed() {
        binding.purchaseDetails.text = "You are subscribed!! Enjoy!!"
        binding.recoverSubscriptionButton.hide()
    }

    private fun renderProducts(state: ViewState) {
        val yearly = state.yearlySubscription
        val monthly = state.monthlySubscription
        val productDetails = state.subscriptionDetails!!
        binding.description.text = productDetails.description
        binding.name.text = productDetails.name
        yearly?.let {
            binding.buyButtonPlan1.apply {
                text = yearly.getPrice()
                setOnClickListener {
                    viewModel.buySubscription(this@SubscriptionsActivity, productDetails, yearly.offerToken)
                }
            }
        }
        monthly?.let {
            binding.buyButtonPlan2.apply {
                text = monthly.getPrice()
                setOnClickListener {
                    viewModel.buySubscription(this@SubscriptionsActivity, productDetails, monthly.offerToken)
                }
            }
        }
    }

    private fun onPurchaseRecovered() {
        TextAlertDialogBuilder(this)
            .setTitle("You're all set.")
            .setMessage("Your already had a subscription and we've recovered that for you.")
            .setPositiveButton(string.ok)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        globalActivityStarter.start(this@SubscriptionsActivity, SubscriptionsSettingsScreenWithEmptyParams)
                        finish()
                    }
                },
            )
            .show()
    }

    private fun onPurchaseSuccess() {
        TextAlertDialogBuilder(this)
            .setTitle("You're all set.")
            .setMessage("Your purchase was successful")
            .setPositiveButton(string.ok)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        globalActivityStarter.start(this@SubscriptionsActivity, SubscriptionsSettingsScreenWithEmptyParams)
                        finish()
                    }
                },
            )
            .show()
    }

    private fun onPurchaseFailure(message: String) {
        TextAlertDialogBuilder(this)
            .setTitle("Something went wrong :(")
            .setMessage(message)
            .setDestructiveButtons(true)
            .setPositiveButton(string.ok)
            .setNegativeButton(string.ok)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        // NOOP
                    }

                    override fun onNegativeButtonClicked() {
                        // NOOP
                    }
                },
            )
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(com.duckduckgo.subscriptions.impl.R.menu.menu_subscriptions_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            id.signOut -> {
                viewModel.signOut()
                true
            }
            id.forceNewAccount -> {
                val viewState = runBlocking {
                    viewModel.viewState.last()
                }
                viewModel.buySubscription(
                    this@SubscriptionsActivity,
                    viewState.subscriptionDetails!!,
                    viewState.monthlySubscription!!.offerToken,
                    isReset = true,
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        object SubscriptionsScreenWithEmptyParams : GlobalActivityStarter.ActivityParams
    }
}
