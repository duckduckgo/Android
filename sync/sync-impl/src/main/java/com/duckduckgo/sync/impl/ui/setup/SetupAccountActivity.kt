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

package com.duckduckgo.sync.impl.ui.setup

import android.content.Context
import android.content.Intent
import android.os.*
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.*
import com.duckduckgo.app.global.*
import com.duckduckgo.di.scopes.*
import com.duckduckgo.mobile.android.ui.viewbinding.*
import com.duckduckgo.sync.impl.R.id
import com.duckduckgo.sync.impl.databinding.*
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.AskSyncAnotherDevice
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.AskSaveRecoveryCode
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.TurnOnSync
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewState
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowFragment.SetupFlowListener
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.ViewMode.InitialSetupScreen
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.ViewMode.SyncAnotherDeviceScreen
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class SetupAccountActivity : DuckDuckGoActivity(), SetupFlowListener {
    private val binding: ActivitySyncSetupAccountBinding by viewBinding()
    private val viewModel: SetupAccountViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        observeUiEvents()
        configureListeners()
    }

    private fun configureListeners() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.onBackPressed()
                }
            },
        )
    }

    private fun observeUiEvents() {
        viewModel
            .viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)

        viewModel
            .commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(it: Command) {
        when (it) {
            Close -> finish()
        }
    }

    private fun renderViewState(viewState: ViewState) {
        when (viewState.viewMode) {
            AskSyncAnotherDevice -> {
                supportFragmentManager.commitNow {
                    replace(id.fragment_container_view, SyncSetupFlowFragment.instance(SyncAnotherDeviceScreen), TAG_RECOVER_ACCOUNT)
                }
            }
            TurnOnSync -> {
                supportFragmentManager.commitNow {
                    replace(id.fragment_container_view, SyncSetupFlowFragment.instance(InitialSetupScreen), TAG_ENABLE_SYNC)
                }
            }
            AskSaveRecoveryCode -> {
                supportFragmentManager.commitNow {
                    replace(id.fragment_container_view, SaveRecoveryCodeFragment.instance(), TAG_ENABLE_SYNC)
                }
            }
        }
    }

    override fun askSyncAnotherDevice() {
        viewModel.onAskSyncAnotherDevice()
    }

    override fun recoverYourSyncedData() {
        TODO("Not yet implemented")
    }

    override fun syncAnotherDevice() {
        TODO("Not yet implemented")
    }

    override fun launchFinishSetupFlow() {
        viewModel.finishSetupFlow()
    }

    companion object {
        private const val TAG_ENABLE_SYNC = "tag_enable_sync"
        private const val TAG_RECOVER_ACCOUNT = "tag_recover_account"
        fun intent(context: Context): Intent {
            return Intent(context, SetupAccountActivity::class.java)
        }
    }
}
