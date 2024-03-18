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

package com.duckduckgo.networkprotection.impl.waitlist

import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetPWaitlistInvitedScreenNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.InBeta
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.JoinedWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.NotUnlocked
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.PendingInviteCode
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.ActivityNetpWaitlistBinding
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistRedeemCodeActivity.Launch.NetPWaitlistRedeemCodeScreenNoParams
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistViewModel.Command
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistViewModel.Command.EnterInviteCode
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistViewModel.ViewState
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(NetPWaitlistScreenNoParams::class)
class NetPWaitlistActivity : DuckDuckGoActivity() {

    @Inject lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject lateinit var appBuildConfig: AppBuildConfig

    private val viewModel: NetPWaitlistViewModel by bindViewModel()
    private val binding: ActivityNetpWaitlistBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    private val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        viewModel.onCodeRedeemed(result.resultCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { render(it) }
            .launchIn(lifecycleScope)
        viewModel.commands.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { executeCommand(it) }
            .launchIn(lifecycleScope)

        setContentView(binding.root)
        setupToolbar(toolbar)
        configureUiEventHandlers()
    }

    private fun configureUiEventHandlers() {
        binding.joinWaitlistButton.setOnClickListener { viewModel.onJoinWaitlistClicked() }
        binding.enterInviteCode.setOnClickListener {
            // this button should only be visible in internal builds
            openRedeemCode()
        }
    }

    private fun render(viewState: ViewState) {
        when (viewState.waitlist) {
            is NotUnlocked, PendingInviteCode -> renderNotJoinedQueue() // Should not happen
            is JoinedWaitlist -> renderJoinedWaitlist()
            is InBeta -> openInviteCode()
        }
    }

    private fun renderNotJoinedQueue() {
        binding.headerImage.setImageResource(R.drawable.ilustration_network_protetion_vpn)
        binding.joinWaitlistButton.show()
        binding.enterInviteCode.isVisible = appBuildConfig.isInternalBuild()
        binding.waitlistNotifyMeContainer.gone()
    }

    private fun renderJoinedWaitlist() {
        binding.statusTitle.text = getString(R.string.netpWaitlistJoined)
        binding.waitlistDescription.text = getString(R.string.netpWaitlistJoinedNotificationsEnabled)
        binding.headerImage.setImageResource(R.drawable.ilustration_success)
        binding.joinWaitlistButton.gone()
        binding.enterInviteCode.gone()
        binding.waitlistNotifyMeContainer.show()
    }

    private fun executeCommand(command: Command) {
        when (command) {
            is EnterInviteCode -> openRedeemCode()
        }
    }

    private fun openRedeemCode() {
        startForResult.launch(globalActivityStarter.startIntent(this, NetPWaitlistRedeemCodeScreenNoParams))
    }

    private fun openInviteCode() {
        globalActivityStarter.start(this, NetPWaitlistInvitedScreenNoParams)
        finish()
    }
}
internal object NetPWaitlistScreenNoParams : ActivityParams
