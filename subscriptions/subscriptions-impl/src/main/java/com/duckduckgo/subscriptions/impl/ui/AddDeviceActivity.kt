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
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.ActivityAddDeviceBinding
import com.duckduckgo.subscriptions.impl.ui.AddDeviceActivity.Companion.AddDeviceScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.ui.AddDeviceViewModel.Command
import com.duckduckgo.subscriptions.impl.ui.AddDeviceViewModel.Command.AddEmail
import com.duckduckgo.subscriptions.impl.ui.AddDeviceViewModel.Command.Error
import com.duckduckgo.subscriptions.impl.ui.AddDeviceViewModel.Command.ManageEmail
import com.duckduckgo.subscriptions.impl.ui.AddDeviceViewModel.ViewState
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(AddDeviceScreenWithEmptyParams::class)
class AddDeviceActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val viewModel: AddDeviceViewModel by bindViewModel()
    private val binding: ActivityAddDeviceBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(toolbar)

        lifecycle.addObserver(viewModel)

        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach {
            renderView(it)
        }.launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)

        binding.email.setPrimaryButtonClickListener {
            viewModel.useEmail()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(viewModel)
    }

    private fun renderView(viewState: ViewState) {
        if (viewState.email != null) {
            binding.email.setSecondaryText(String.format(getString(R.string.useEmail), viewState.email))
            binding.email.setPrimaryButtonText(getString(R.string.manage))
        } else {
            binding.email.setPrimaryButtonText(getString(R.string.addEmailText))
            binding.email.setSecondaryText(getString(R.string.addEmail))
        }
    }

    private fun goToManageEmail() {
        globalActivityStarter.start(
            this,
            SubscriptionsWebViewActivityWithParams(
                url = MANAGE_URL,
                screenTitle = getString(R.string.manageEmail),
                defaultToolbar = false,
            ),
        )
    }

    private fun goToAddEmail() {
        globalActivityStarter.start(
            this,
            SubscriptionsWebViewActivityWithParams(
                url = ADD_EMAIL_URL,
                getString(R.string.addEmailText),
                defaultToolbar = false,
            ),
        )
    }

    private fun showError() {
        Toast.makeText(this, R.string.randomError, Toast.LENGTH_SHORT).show()
    }

    private fun processCommand(command: Command) {
        when (command) {
            is AddEmail -> goToAddEmail()
            is ManageEmail -> goToManageEmail()
            is Error -> showError()
        }
    }
    companion object {
        const val ADD_EMAIL_URL = "https://duckduckgo.com/subscriptions/add-email?environment=staging"
        const val MANAGE_URL = "https://duckduckgo.com/subscriptions/manage?environment=staging"
        object AddDeviceScreenWithEmptyParams : GlobalActivityStarter.ActivityParams
    }
}
