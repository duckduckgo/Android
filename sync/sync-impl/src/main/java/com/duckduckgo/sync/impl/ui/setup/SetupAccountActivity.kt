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
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R.id
import com.duckduckgo.sync.impl.databinding.ActivitySyncSetupAccountBinding
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.DEVICE_SYNCED
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.RECOVERY_CODE
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.SYNC_SETUP
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.AskSaveRecoveryCode
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.CreateAccount
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.DeviceSynced
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class SetupAccountActivity : DuckDuckGoActivity(), SetupFlowListener {
    private val binding: ActivitySyncSetupAccountBinding by viewBinding()
    private val viewModel: SetupAccountViewModel by bindViewModel()

    private lateinit var screen: Screen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = if (savedInstanceState != null) {
            savedInstanceState.getSerializable(SETUP_ACCOUNT_SCREEN_EXTRA) as Screen
        } else {
            intent.getSerializableExtra(SETUP_ACCOUNT_SCREEN_EXTRA) as? Screen ?: DEVICE_SYNCED
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
            AskSaveRecoveryCode -> {
                screen = RECOVERY_CODE
                supportFragmentManager.commitNow {
                    replace(id.fragment_container_view, SaveRecoveryCodeFragment.instance(), TAG_RECOVER_ACCOUNT)
                }
            }

            DeviceSynced -> {
                screen = DEVICE_SYNCED
                supportFragmentManager.commitNow {
                    replace(id.fragment_container_view, SyncDeviceConnectedFragment.instance(), TAG_DEVICE_CONNECTED)
                }
            }

            CreateAccount -> {
                screen = SYNC_SETUP
                supportFragmentManager.commitNow {
                    replace(id.fragment_container_view, SyncCreateAccountFragment.instance(), TAG_SYNC_SETUP)
                }
            }
        }
    }

    override fun launchFinishSetupFlow() {
        viewModel.onSetupComplete()
    }

    companion object {
        private const val TAG_SYNC_SETUP = "tag_sync_setup"
        private const val TAG_RECOVER_ACCOUNT = "tag_recover_account"
        private const val TAG_DEVICE_CONNECTED = "tag_device_connected"

        enum class Screen {
            SYNC_SETUP,
            RECOVERY_CODE,
            DEVICE_SYNCED,
        }

        const val SETUP_ACCOUNT_SCREEN_EXTRA = "SETUP_ACCOUNT_SCREEN_EXTRA"

        internal fun intentSetupFlow(context: Context): Intent {
            return Intent(context, SetupAccountActivity::class.java).apply {
                putExtra(SETUP_ACCOUNT_SCREEN_EXTRA, SYNC_SETUP)
            }
        }

        internal fun intentDeviceConnectedFlow(context: Context): Intent {
            return Intent(context, SetupAccountActivity::class.java).apply {
                putExtra(SETUP_ACCOUNT_SCREEN_EXTRA, DEVICE_SYNCED)
            }
        }
    }
}
