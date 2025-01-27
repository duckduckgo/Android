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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.impl.databinding.LegacyViewSettingsNetpBinding
import com.duckduckgo.networkprotection.impl.subscription.settings.LegacyProSettingNetPViewModel.Command
import com.duckduckgo.networkprotection.impl.subscription.settings.LegacyProSettingNetPViewModel.Command.OpenNetPScreen
import com.duckduckgo.networkprotection.impl.subscription.settings.LegacyProSettingNetPViewModel.Factory
import com.duckduckgo.networkprotection.impl.subscription.settings.LegacyProSettingNetPViewModel.NetPEntryState
import com.duckduckgo.networkprotection.impl.subscription.settings.LegacyProSettingNetPViewModel.NetPEntryState.Hidden
import com.duckduckgo.networkprotection.impl.subscription.settings.LegacyProSettingNetPViewModel.NetPEntryState.Pending
import com.duckduckgo.networkprotection.impl.subscription.settings.LegacyProSettingNetPViewModel.NetPEntryState.ShowState
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ViewScope::class)
class LegacyProSettingNetPView @JvmOverloads constructor(
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

    private val binding: LegacyViewSettingsNetpBinding by viewBinding()

    private val viewModel: LegacyProSettingNetPViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[LegacyProSettingNetPViewModel::class.java]
    }

    private val conflatedStateJob = ConflatedJob()
    private val conflatedCommandJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        binding.netpPSetting.setClickListener {
            viewModel.onNetPSettingClicked()
        }

        val coroutineScope = findViewTreeLifecycleOwner()?.lifecycleScope

        conflatedStateJob += viewModel.viewState
            .onEach { updateNetPSettings(it.networkProtectionEntryState) }
            .launchIn(coroutineScope!!)

        conflatedCommandJob += viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)
    }

    private fun updateNetPSettings(networkProtectionEntryState: NetPEntryState) {
        with(binding.netpPSetting) {
            when (networkProtectionEntryState) {
                Hidden -> this.gone()
                Pending -> {
                    this.show()
                    this.setLeadingIconResource(CommonR.drawable.ic_check_grey_round_16)
                }
                is ShowState -> {
                    this.show()
                    this.setLeadingIconResource(networkProtectionEntryState.icon)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        conflatedStateJob.cancel()
        conflatedCommandJob.cancel()
    }

    private fun processCommands(command: Command) {
        when (command) {
            is OpenNetPScreen -> {
                globalActivityStarter.start(context, command.params)
            }
        }
    }
}
