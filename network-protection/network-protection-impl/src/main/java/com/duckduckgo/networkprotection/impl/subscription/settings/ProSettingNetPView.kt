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

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.ViewSettingsNetpBinding
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.Command
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.Command.OpenNetPScreen
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.Factory
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState.Hidden
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState.Pending
import com.duckduckgo.networkprotection.impl.subscription.settings.ProSettingNetPViewModel.NetPEntryState.ShowState
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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

    private var coroutineScope: CoroutineScope? = null

    private val binding: ViewSettingsNetpBinding by viewBinding()

    private val viewModel: ProSettingNetPViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[ProSettingNetPViewModel::class.java]
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        binding.netpPSetting.setClickListener {
            viewModel.onNetPSettingClicked()
        }

        @SuppressLint("NoHardcodedCoroutineDispatcher")
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        viewModel.viewState
            .onEach { updateNetPSettings(it.networkProtectionEntryState) }
            .launchIn(coroutineScope!!)

        viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)
    }

    private fun updateNetPSettings(networkProtectionEntryState: NetPEntryState) {
        with(binding.netpPSetting) {
            when (networkProtectionEntryState) {
                Hidden -> this.gone()
                Pending -> {
                    this.show()
                    this.setSecondaryText(context.getString(R.string.netpSubscriptionSettingsNeverEnabled))
                    this.setItemStatus(com.duckduckgo.common.ui.view.listitem.CheckListItem.CheckItemStatus.DISABLED)
                }
                is ShowState -> {
                    this.show()
                    this.setSecondaryText(context.getString(networkProtectionEntryState.subtitle))
                    this.setItemStatus(networkProtectionEntryState.icon)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        coroutineScope?.cancel()
        coroutineScope = null
    }

    private fun processCommands(command: Command) {
        when (command) {
            is OpenNetPScreen -> {
                globalActivityStarter.start(context, command.params)
            }
        }
    }
}
