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
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.sync.impl.R.id
import com.duckduckgo.sync.impl.databinding.ActivitySyncSetupAccountBinding
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsLaunchSource
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsParams
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.RECOVERY_CODE
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.RECOVERY_INTRO
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.SETUP_COMPLETE
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.SYNC_INTRO
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.SYNC_SETUP
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command.Finish
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command.LaunchSyncGetOnOtherPlatforms
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command.RecoverAccount
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.AskSaveRecoveryCode
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.CreateAccount
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.IntroCreateAccount
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.IntroRecoveryCode
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode.SyncSetupCompleted
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewState
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class SetupAccountActivity : DuckDuckGoActivity(), SyncSetupNavigationFlowListener, SyncSetupFlowFinishedListener {
    private val binding: ActivitySyncSetupAccountBinding by viewBinding()
    private val viewModel: SetupAccountViewModel by bindViewModel()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private lateinit var screen: Screen

    private val loginFlow = registerForActivityResult(LoginContract()) { resultOk ->
        if (resultOk) {
            viewModel.onCreateAccount()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = if (savedInstanceState != null) {
            savedInstanceState.getSerializable(SETUP_ACCOUNT_SCREEN_EXTRA) as Screen
        } else {
            intent.getSerializableExtra(SETUP_ACCOUNT_SCREEN_EXTRA) as? Screen ?: SETUP_COMPLETE
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
            RecoverAccount -> loginFlow.launch(null)
            Finish -> {
                setResult(Activity.RESULT_OK)
                finish()
            }
            is LaunchSyncGetOnOtherPlatforms -> launchSyncGetOnOtherPlatforms(it.source)
        }
    }

    private fun renderViewState(viewState: ViewState) {
        when (viewState.viewMode) {
            CreateAccount -> {
                screen = SYNC_SETUP
                supportFragmentManager.commitNow {
                    replace(id.fragment_container_view, SyncCreateAccountFragment.instance(extractSource()), TAG_SYNC_SETUP)
                }
            }
            IntroCreateAccount -> {
                screen = SYNC_INTRO
                supportFragmentManager.commitNow {
                    replace(id.fragment_container_view, SyncSetupIntroFragment.instance(SYNC_INTRO), TAG_SYNC_INTRO)
                }
            }

            AskSaveRecoveryCode -> {
                screen = RECOVERY_CODE
                supportFragmentManager.commitNow {
                    replace(id.fragment_container_view, SaveRecoveryCodeFragment.instance(), TAG_RECOVER_ACCOUNT)
                }
            }

            IntroRecoveryCode -> {
                screen = RECOVERY_INTRO
                supportFragmentManager.commitNow {
                    replace(id.fragment_container_view, SyncSetupIntroFragment.instance(RECOVERY_INTRO), TAG_SYNC_INTRO)
                }
            }

            SyncSetupCompleted -> {
                screen = SETUP_COMPLETE
                supportFragmentManager.commitNow {
                    replace(id.fragment_container_view, SyncDeviceConnectedFragment.instance(), TAG_DEVICE_CONNECTED)
                }
            }
        }
    }

    override fun launchDeviceConnectedScreen() {
        viewModel.onDeviceConnected()
    }

    override fun launchGetAppOnOtherPlatformsScreen() {
        viewModel.onGetAppOnOtherDevicesClicked()
    }

    override fun finishSetup() {
        viewModel.onSetupFinished()
    }

    override fun launchRecoveryCodeScreen() {
        viewModel.onRecoveryCodePrompt()
    }

    override fun launchCreateAccountScreen() {
        viewModel.onCreateAccount()
    }

    override fun launchRecoverAccountScreen() {
        viewModel.onRecoverAccount()
    }

    private fun launchSyncGetOnOtherPlatforms(source: SyncGetOnOtherPlatformsLaunchSource) {
        globalActivityStarter.start(this, SyncGetOnOtherPlatformsParams(source))
    }

    private fun extractSource(): String? {
        return intent.getStringExtra(LAUNCH_SOURCE_EXTRA)
    }

    companion object {
        private const val TAG_SYNC_SETUP = "tag_sync_setup"
        private const val TAG_SYNC_INTRO = "tag_sync_intro"
        private const val TAG_RECOVER_ACCOUNT = "tag_recover_account"
        private const val TAG_DEVICE_CONNECTED = "tag_device_connected"

        const val SETUP_ACCOUNT_SCREEN_EXTRA = "SETUP_ACCOUNT_SCREEN_EXTRA"
        const val LAUNCH_SOURCE_EXTRA = "LAUNCH_SOURCE_EXTRA"

        enum class Screen {
            SYNC_SETUP,
            SYNC_INTRO,
            RECOVERY_CODE,
            RECOVERY_INTRO,
            SETUP_COMPLETE,
        }

        internal fun intent(context: Context, screen: Screen, source: String?): Intent {
            return Intent(context, SetupAccountActivity::class.java).apply {
                putExtra(SETUP_ACCOUNT_SCREEN_EXTRA, screen)
                putExtra(LAUNCH_SOURCE_EXTRA, source)
            }
        }
    }
}
