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

package com.duckduckgo.networkprotection.impl.subscription.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.ViewSettingsNetpBinding
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.Command
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.Command.OpenNetPScreen
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.Factory
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState.Disabled
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState.Enabled
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState.Hidden
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ViewScope::class)
class ProSettingNetPView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: Factory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val binding: ViewSettingsNetpBinding by viewBinding()

    private val viewModel: ProSettingNetPViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[ProSettingNetPViewModel::class.java]
    }
    private val conflatedStateJob = ConflatedJob()
    private val conflatedCommandJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        val coroutineScope = findViewTreeLifecycleOwner()?.lifecycleScope

        conflatedStateJob += viewModel.viewState
            .onEach { updateNetPSettings(it.netPEntryState) }
            .launchIn(coroutineScope!!)

        conflatedCommandJob += viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)
    }

    private fun updateNetPSettings(networkProtectionEntryState: NetPEntryState) {
        with(binding.netpPSetting) {
            when (networkProtectionEntryState) {
                Hidden -> isGone = true
                is Disabled -> {
                    isVisible = true
                    isClickable = false
                    setClickListener(null)
                    setLeadingIconResource(R.drawable.ic_vpn_grayscale_color_24)
                    setStatus(isOn = false)
                }
                is Enabled -> {
                    isVisible = true
                    isClickable = true
                    setClickListener { viewModel.onNetPSettingClicked() }
                    setLeadingIconResource(R.drawable.ic_vpn_color_24)
                    setStatus(isOn = networkProtectionEntryState.isActive)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        conflatedCommandJob.cancel()
        conflatedStateJob.cancel()
    }

    private fun processCommands(command: Command) {
        when (command) {
            is OpenNetPScreen -> {
                globalActivityStarter.start(context, command.params)
            }
        }
    }
}
