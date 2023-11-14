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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.networkprotection.api.NetPWaitlistInvitedScreenNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionManagementScreenAndEnable
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.InBeta
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.JoinedWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.NotUnlocked
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.PendingInviteCode
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.VerifySubscription
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.about.NetPTermsScreenNoParams
import com.duckduckgo.networkprotection.impl.databinding.ActivityNetpWaitlistInvitedBinding
import com.duckduckgo.networkprotection.impl.waitlist.NetPInviteCodeViewModel.Command
import com.duckduckgo.networkprotection.impl.waitlist.NetPInviteCodeViewModel.Command.EnterInviteCode
import com.duckduckgo.networkprotection.impl.waitlist.NetPInviteCodeViewModel.Command.OpenNetP
import com.duckduckgo.networkprotection.impl.waitlist.NetPInviteCodeViewModel.Command.OpenTermsScreen
import com.duckduckgo.networkprotection.impl.waitlist.NetPInviteCodeViewModel.ViewState
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistInvitedActivity.Companion.NetPWaitlistInvitedScreenWithOriginPixels
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistRedeemCodeActivity.Launch.NetPWaitlistRedeemCodeScreenNoParams
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(NetPWaitlistInvitedScreenNoParams::class)
@ContributeToActivityStarter(NetPWaitlistInvitedScreenWithOriginPixels::class)
class NetPWaitlistInvitedActivity : DuckDuckGoActivity() {

    @Inject lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject lateinit var netPWaitlistRepository: NetPWaitlistRepository

    @Inject lateinit var pixel: Pixel

    private val viewModel: NetPInviteCodeViewModel by bindViewModel()
    private val binding: ActivityNetpWaitlistInvitedBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    private val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            renderJoinedBeta()
        }
    }

    private val startTermsForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            netPWaitlistRepository.acceptWaitlistTerms()
            viewModel.onTermsAccepted()
        }
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

        // fire pixels
        intent?.getActivityParams(NetPWaitlistInvitedScreenWithOriginPixels::class.java)?.pixelNames?.forEach { pixelName ->
            pixel.fire(pixelName)
        }
    }

    private fun configureUiEventHandlers() {
        binding.enterCodeButton.setOnClickListener { viewModel.haveAnInviteCode() }
        binding.getStartedButton.setOnClickListener {
            viewModel.getStarted()
        }
    }

    private fun render(viewState: ViewState) {
        when (viewState.waitlist) {
            is NotUnlocked, PendingInviteCode -> renderNotJoinedQueue() // Should not happen
            is JoinedWaitlist -> {}
            is InBeta, VerifySubscription -> renderJoinedBeta()
        }
    }
    private fun renderNotJoinedQueue() {
        binding.headerImage.setImageResource(R.drawable.ic_lock)
        binding.getStartedButton.gone()
        binding.waitlistDescriptionItems.gone()
        binding.enterCodeButton.show()
    }

    private fun renderJoinedBeta() {
        binding.statusTitle.text = getString(R.string.netpWaitlistRedeemedCodeStatus)
        binding.headerImage.setImageResource(R.drawable.ic_gift_large)
        binding.getStartedButton.show()
        binding.waitlistDescriptionItems.show()
        binding.enterCodeButton.gone()
    }

    private fun executeCommand(command: Command) {
        when (command) {
            is EnterInviteCode -> openRedeemCode()
            is OpenTermsScreen -> openTermsScreen()
            is OpenNetP -> openAndEnableNetP()
        }
    }

    private fun openRedeemCode() {
        startForResult.launch(globalActivityStarter.startIntent(this, NetPWaitlistRedeemCodeScreenNoParams))
    }

    private fun openTermsScreen() {
        startTermsForResult.launch(globalActivityStarter.startIntent(this, NetPTermsScreenNoParams))
    }

    private fun openAndEnableNetP() {
        globalActivityStarter.start(this, NetworkProtectionManagementScreenAndEnable(true))
        finish()
    }

    companion object {
        /**
         * Use this model to launch the NetP invite code screen and fire impression pixels
         */
        internal data class NetPWaitlistInvitedScreenWithOriginPixels(val pixelNames: List<String>) : ActivityParams
    }
}
