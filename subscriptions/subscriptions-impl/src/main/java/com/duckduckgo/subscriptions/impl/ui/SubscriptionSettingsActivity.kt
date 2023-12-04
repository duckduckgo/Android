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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.impl.R.string
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.AutoRenewable
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.BASIC_SUBSCRIPTION
import com.duckduckgo.subscriptions.impl.databinding.ActivitySubscriptionSettingsBinding
import com.duckduckgo.subscriptions.impl.ui.AddDeviceActivity.Companion.AddDeviceScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsActivity.Companion.SubscriptionsSettingsScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.FinishSignOut
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Monthly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.ViewState
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SubscriptionsSettingsScreenWithEmptyParams::class)
class SubscriptionSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val viewModel: SubscriptionSettingsViewModel by bindViewModel()
    private val binding: ActivitySubscriptionSettingsBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(toolbar)

        lifecycle.addObserver(viewModel)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)

        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach {
            renderView(it)
        }.launchIn(lifecycleScope)

        binding.addDevice.setClickListener {
            globalActivityStarter.start(this, AddDeviceScreenWithEmptyParams)
        }

        binding.removeDevice.setClickListener {
            TextAlertDialogBuilder(this)
                .setTitle(string.removeFromDevice)
                .setMessage(string.removeFromDeviceDescription)
                .setDestructiveButtons(true)
                .setPositiveButton(string.removeSubscription)
                .setNegativeButton(string.cancel)
                .addEventListener(
                    object : TextAlertDialogBuilder.EventListener() {
                        override fun onPositiveButtonClicked() {
                            viewModel.removeFromDevice()
                        }

                        override fun onNegativeButtonClicked() {
                            // NOOP
                        }
                    },
                )
                .show()
        }

        binding.changePlan.setClickListener {
            val url = String.format(URL, BASIC_SUBSCRIPTION, applicationContext.packageName)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setData(Uri.parse(url))
            startActivity(intent)
        }

        binding.faq.setClickListener {
            Toast.makeText(this, "This will take you to FAQs", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(viewModel)
    }

    private fun renderView(viewState: ViewState) {
        val duration = if (viewState.duration is Monthly) {
            getString(string.monthly)
        } else {
            getString(string.yearly)
        }

        val status = when (viewState.status) {
            is AutoRenewable -> getString(string.renews)
            else -> getString(string.expires)
        }

        binding.description.text = getString(string.subscriptionsData, duration, status, viewState.date)
    }

    private fun processCommand(command: Command) {
        when (command) {
            is FinishSignOut -> {
                Toast.makeText(this, string.subscriptionRemoved, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    companion object {
        const val URL = "https://play.google.com/store/account/subscriptions?sku=%s&package=%s"
        object SubscriptionsSettingsScreenWithEmptyParams : GlobalActivityStarter.ActivityParams
    }
}
