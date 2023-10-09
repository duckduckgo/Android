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

package com.duckduckgo.subscriptions.impl

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
import com.duckduckgo.mobile.android.ui.view.dialog.ActionBottomSheetDialog
import com.duckduckgo.mobile.android.ui.view.hide
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.impl.SubscriptionsActivity.Companion.SubscriptionsScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.SubscriptionsViewModel.Command
import com.duckduckgo.subscriptions.impl.SubscriptionsViewModel.Command.ErrorMessage
import com.duckduckgo.subscriptions.impl.SubscriptionsViewModel.ViewState
import com.duckduckgo.subscriptions.impl.billing.getPrice
import com.duckduckgo.subscriptions.impl.databinding.ActivitySubscriptionsBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SubscriptionsScreenWithEmptyParams::class)
class SubscriptionsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val viewModel: SubscriptionsViewModel by bindViewModel()
    private val binding: ActivitySubscriptionsBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    private var bottomSheet: ActionBottomSheetDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.start()
        viewModel.viewState().flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach {
            if (it.subscriptionDetails != null) {
                renderProducts(it)
            }
            if (it.hasSubscription == true) {
                renderSubscribed()
            } else {
                renderNotSubscribed()
            }
        }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)

        setContentView(binding.root)
        setupToolbar(toolbar)

        binding.recoverSubscriptionButton.setOnClickListener {
            val builder = ActionBottomSheetDialog.Builder(this)
                .setTitle("Recover subscription")
                .setPrimaryItem("Use Google Play Account", R.drawable.ic_platform_macos_16)
                .setSecondaryItem("Use Email", R.drawable.ic_email_16)
                .addEventListener(
                    object : ActionBottomSheetDialog.EventListener() {
                        override fun onPrimaryItemClicked() {
                            viewModel.recoverSubscription()
                            bottomSheet?.dismiss()
                        }

                        override fun onSecondaryItemClicked() {
                            launchRecoverScreen()
                            bottomSheet?.dismiss()
                        }
                    },
                )
            bottomSheet = ActionBottomSheetDialog(builder)
            bottomSheet?.show()
        }

        binding.addDevices.setOnClickListener {
            globalActivityStarter.start(
                this,
                SubscriptionsWebViewActivityWithParams(
                    url = "https://abrown.duckduckgo.com/subscriptions/add-email",
                    "Add Devices",
                ),
            )
        }

        binding.manageAccountButton.setOnClickListener {
            globalActivityStarter.start(
                this,
                SubscriptionsWebViewActivityWithParams(
                    url = "https://abrown.duckduckgo.com/subscriptions/manage",
                    "Manage Account",
                ),
            )
        }
    }

    private fun launchRecoverScreen() {
        globalActivityStarter.start(
            this,
            SubscriptionsWebViewActivityWithParams(
                url = "https://abrown.duckduckgo.com/subscriptions/activate",
                "Recover Subscription",
            ),
        )
    }

    private fun processCommand(command: Command) {
        if (command is ErrorMessage) {
            Toast.makeText(this, command.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun renderNotSubscribed() {
        binding.purchaseDetails.text = "You are not subscribed yet"
        binding.recoverSubscriptionButton.show()
        binding.addDevices.hide()
        binding.manageAccountButton.hide()
    }
    private fun renderSubscribed() {
        binding.purchaseDetails.text = "You are subscribed!! Enjoy!!"
        binding.recoverSubscriptionButton.hide()
        binding.addDevices.show()
        binding.manageAccountButton.show()
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(com.duckduckgo.subscriptions.impl.R.menu.menu_subscriptions_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            com.duckduckgo.subscriptions.impl.R.id.signOut -> {
                viewModel.signOut()
                true
            }
            com.duckduckgo.subscriptions.impl.R.id.forceNewAccount -> {
                val viewState = viewModel.viewState().value
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
