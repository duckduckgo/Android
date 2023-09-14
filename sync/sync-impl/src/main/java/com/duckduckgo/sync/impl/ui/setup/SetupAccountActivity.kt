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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.sync.impl.R.id
import com.duckduckgo.sync.impl.databinding.ActivitySyncSetupAccountBinding
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.DEVICE_CONNECTED
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.INITIALISE
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.RECOVERY_CODE
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.AskSaveRecoveryCode
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.DeviceConnected
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.TurnOnSync
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewState
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.ViewMode.SyncInitializedScreen
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class SetupAccountActivity : DuckDuckGoActivity(), SetupFlowListener {
    private val binding: ActivitySyncSetupAccountBinding by viewBinding()
    private val viewModel: SetupAccountViewModel by bindViewModel()

    private lateinit var screen: Screen

    private val loginFlow = registerForActivityResult(LoginContract()) { resultOk ->
        if (resultOk) {
            viewModel.onLoginSuccess()
        }
    }

    private val connectFlow = registerForActivityResult(ConnectFlowContract()) { resultOk ->
        if (resultOk) {
            viewModel.onLoginSuccess()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = if (savedInstanceState != null) {
            savedInstanceState.getSerializable(SETUP_ACCOUNT_SCREEN_EXTRA) as Screen
        } else {
            intent.getSerializableExtra(SETUP_ACCOUNT_SCREEN_EXTRA) as? Screen ?: INITIALISE
        }
        setContentView(binding.root)
        observeUiEvents()
        configureListeners()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(SETUP_ACCOUNT_SCREEN_EXTRA, screen)
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
            .viewState(screen)
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
            Close -> {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
    }

    private fun renderViewState(viewState: ViewState) {
        when (viewState.viewMode) {
            TurnOnSync -> {
                screen = INITIALISE
                supportFragmentManager.commitNow {
                    replace(id.fragment_container_view, SyncSetupFlowFragment.instance(SyncInitializedScreen), TAG_ENABLE_SYNC)
                }
            }

            AskSaveRecoveryCode -> {
                screen = RECOVERY_CODE
                supportFragmentManager.commitNow {
                    replace(id.fragment_container_view, SaveRecoveryCodeFragment.instance(), TAG_RECOVER_ACCOUNT)
                }
            }

            DeviceConnected -> {
                screen = DEVICE_CONNECTED
                supportFragmentManager.commitNow {
                    replace(id.fragment_container_view, SyncDeviceConnectedFragment.instance(), TAG_DEVICE_CONNECTED)
                }
            }
        }
    }


    override fun launchFinishSetupFlow() {
        viewModel.onSetupComplete()
    }

    companion object {
        private const val TAG_ENABLE_SYNC = "tag_enable_sync"
        private const val TAG_RECOVER_ACCOUNT = "tag_recover_account"
        private const val TAG_DEVICE_CONNECTED = "tag_device_connected"

        enum class Screen {
            INITIALISE,
            RECOVERY_CODE,
            DEVICE_CONNECTED,
        }

        const val SETUP_ACCOUNT_SCREEN_EXTRA = "SETUP_ACCOUNT_SCREEN_EXTRA"

        internal fun intentStartSetupFlow(context: Context): Intent {
            return Intent(context, SetupAccountActivity::class.java).apply {
                putExtra(SETUP_ACCOUNT_SCREEN_EXTRA, INITIALISE)
            }
        }

        internal fun intentDeviceConnectedFlow(context: Context): Intent {
            return Intent(context, SetupAccountActivity::class.java).apply {
                putExtra(SETUP_ACCOUNT_SCREEN_EXTRA, DEVICE_CONNECTED)
            }
        }
    }

}
