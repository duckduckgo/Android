/*
 * Copyright (c) 2024 DuckDuckGo
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
import android.os.Bundle
import androidx.activity.addCallback
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivityDeepLinkDeviceConnectedBinding
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsParams
import com.duckduckgo.sync.impl.ui.setup.SyncSetupDeepLinkConnectedViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.SyncSetupDeepLinkConnectedViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.setup.SyncSetupDeepLinkConnectedViewModel.Command.LaunchSyncGetOnOtherPlatforms
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class SyncSetupDeepLinkConnectedActivity : DuckDuckGoActivity(), SyncSetupFlowFinishedListener {

    private val binding: ActivityDeepLinkDeviceConnectedBinding by viewBinding()
    private val viewModel: SyncSetupDeepLinkConnectedViewModel by bindViewModel()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        supportFragmentManager.commitNow {
            SyncDeviceConnectedFragment.instance().also { fragment ->
                replace(R.id.fragment_container_view, fragment, FRAGMENT_TAG_DEVICE_CONNECTED)
            }
        }

        onBackPressedDispatcher.addCallback {
            viewModel.onBackPressed()
        }

        viewModel
            .commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: Command) {
        when (command) {
            Close -> finish()
            is LaunchSyncGetOnOtherPlatforms -> globalActivityStarter.start(this, SyncGetOnOtherPlatformsParams(command.source))
        }
    }

    override fun launchGetAppOnOtherPlatformsScreen() {
        viewModel.onGetAppOnOtherDevicesClicked()
    }

    override fun finishSetup() {
        viewModel.onSetupFinished()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, SyncSetupDeepLinkConnectedActivity::class.java)
        }

        private const val FRAGMENT_TAG_DEVICE_CONNECTED = "device-connected"
    }
}
