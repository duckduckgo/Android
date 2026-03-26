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

package com.duckduckgo.networkprotection.impl.settings.custom_dns

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.isPrivateDnsStrict
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.VpnViewSettingsCustomDnsBinding
import com.duckduckgo.networkprotection.impl.settings.CUSTOM_DNS_ITEM_PRIORITY
import com.duckduckgo.networkprotection.impl.settings.VpnSettingPlugin
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsSettingView.Event.Init
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsSettingView.State.CustomDns
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsSettingView.State.Default
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsSettingView.State.DefaultBlockMalware
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsSettingView.State.Idle
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsViewSettingViewModel.Factory
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ViewScope::class)
class VpnCustomDnsSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: Factory

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val events = MutableSharedFlow<Event>(replay = 1, extraBufferCapacity = 1)

    private val binding: VpnViewSettingsCustomDnsBinding by viewBinding()

    private val viewModel: VpnCustomDnsViewSettingViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[VpnCustomDnsViewSettingViewModel::class.java]
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.customDnsSetting.setOnClickListener {
            globalActivityStarter.start(context, VpnCustomDnsScreen.Default)
        }

        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            events
                .flatMapLatest { viewModel.reduce(it) }
                .flowOn(dispatcherProvider.io())
                .onStart { events.emit(Init) }
                .collect(::render)
        }
    }

    override fun onVisibilityChanged(
        changedView: View,
        visibility: Int,
    ) {
        super.onVisibilityChanged(changedView, visibility)
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            // every time is visible, emit to refresh state
            if (visibility == 0) events.emit(Init)
        }
    }

    private fun render(state: State) {
        if (this@VpnCustomDnsSettingView.context.isPrivateDnsStrict()) {
            binding.customDnsSetting.setSecondaryText(context.getString(R.string.netpPrivateDns))
        } else {
            when (state) {
                Idle -> {}
                is CustomDns -> binding.customDnsSetting.setSecondaryText(state.serverName)
                Default, DefaultBlockMalware -> binding.customDnsSetting.setSecondaryText(context.getString(R.string.netpCustomDnsDefault))
            }
        }
    }

    sealed class Event {
        data object Init : Event()
    }

    sealed class State {
        data object Idle : State()
        data object Default : State()
        data object DefaultBlockMalware : State()
        data class CustomDns(val serverName: String) : State()
    }
}

@ContributesMultibinding(ActivityScope::class)
@PriorityKey(CUSTOM_DNS_ITEM_PRIORITY)
class VpnCustomDnsSettingViewPlugin @Inject constructor() : VpnSettingPlugin {
    override fun getView(context: Context): View {
        return VpnCustomDnsSettingView(context)
    }
}
