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

package com.duckduckgo.networkprotection.subscription.ui

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetPWaitlistInvitedScreenNoParams
import com.duckduckgo.networkprotection.subscription.databinding.ActivityNetpVerifySubsBinding
import com.duckduckgo.networkprotection.subscription.ui.NetpSubscriptionScreens.NetpVerifySubscriptionParams
import com.duckduckgo.networkprotection.subscription.ui.NetpVerifySubscriptionViewModel.Command.LaunchNetPScreen
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(NetpVerifySubscriptionParams::class)
class NetpVerifySubscriptionActivity : DuckDuckGoActivity() {

    @Inject lateinit var globalActivityStarter: GlobalActivityStarter

    private val viewModel: NetpVerifySubscriptionViewModel by bindViewModel()
    private val binding: ActivityNetpVerifySubsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    private fun observeViewModel() {
        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach {
                binding.netpVerifySubsStatus.text = getString(it.message)
            }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach {
                if (it == LaunchNetPScreen) {
                    globalActivityStarter.start(this, NetPWaitlistInvitedScreenNoParams)
                    finish()
                }
            }
            .launchIn(lifecycleScope)
    }
}
