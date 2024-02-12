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

package com.duckduckgo.networkprotection.internal.feature.custom_dns

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.quietlySetIsChecked
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.internal.databinding.ActivityNetpCustomDnsBinding
import com.duckduckgo.networkprotection.internal.feature.custom_dns.VpnCustomDnsActivity.Event.Init
import com.duckduckgo.networkprotection.internal.feature.custom_dns.VpnCustomDnsActivity.Event.OnApply
import com.duckduckgo.networkprotection.internal.feature.custom_dns.VpnCustomDnsActivity.State.CustomDns
import com.duckduckgo.networkprotection.internal.feature.custom_dns.VpnCustomDnsActivity.State.DefaultDns
import com.duckduckgo.networkprotection.internal.feature.custom_dns.VpnCustomDnsActivity.State.Done
import com.duckduckgo.networkprotection.internal.feature.custom_dns.VpnCustomDnsActivity.State.NeedApply
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(VpnCustomDnsScreen.Default::class)
class VpnCustomDnsActivity : DuckDuckGoActivity() {

    private val binding: ActivityNetpCustomDnsBinding by viewBinding()
    private val viewModel: VpnCustomDnsViewModel by bindViewModel()

    private val events = MutableSharedFlow<Event>(replay = 1, extraBufferCapacity = 1)

    private val defaultDnsListener = OnCheckedChangeListener { button, value ->
        if (value) {
            lifecycleScope.launch {
                events.emit(Event.DefaultDnsSelected)
            }
        }
    }

    private val customDnsListener = OnCheckedChangeListener { button, value ->
        if (value) {
            lifecycleScope.launch {
                events.emit(Event.CustomDnsEntered(binding.customDns.text))
            }
        }
    }

    private val customDnsTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

        override fun afterTextChanged(p0: Editable?) {
            lifecycleScope.launch {
                events.emit(Event.CustomDnsEntered(p0.toString()))
            }
        }
    }

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    @Inject lateinit var networkProtectionState: NetworkProtectionState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            events
                .flatMapLatest { viewModel.reduce(it) }
                .flowOn(dispatcherProvider.io())
                .onStart { events.emit(Init) }
                .collect(::render)
        }
        binding.defaultDnsOption.setOnCheckedChangeListener(defaultDnsListener)
        binding.customDnsOption.setOnCheckedChangeListener(customDnsListener)
        binding.customDns.addTextChangedListener(customDnsTextWatcher)
        binding.applyCustomDns.setOnClickListener {
            lifecycleScope.launch {
                events.emit(OnApply)
            }
        }
    }

    private fun render(state: State) {
        when (state) {
            DefaultDns -> {
                binding.defaultDnsOption.quietlySetIsChecked(true, defaultDnsListener)
                binding.customDns.removeTextChangedListener(customDnsTextWatcher)
                binding.customDns.isEditable = false
                binding.customDns.addTextChangedListener(customDnsTextWatcher)
            }
            is CustomDns -> {
                binding.customDnsOption.quietlySetIsChecked(true, customDnsListener)
                binding.customDns.removeTextChangedListener(customDnsTextWatcher)
                binding.customDns.text = state.dns
                binding.customDns.isEditable = true
                binding.customDns.addTextChangedListener(customDnsTextWatcher)
            }

            is NeedApply -> binding.applyCustomDns.isEnabled = state.value
            Done -> {
                networkProtectionState.restart()
                finish()
            }
        }
    }

    internal sealed class Event {
        data object Init : Event()
        data class CustomDnsEntered(val dns: String?) : Event()
        data object DefaultDnsSelected : Event()
        data object OnApply : Event()
    }

    internal sealed class State {
        data class NeedApply(val value: Boolean) : State()
        data object DefaultDns : State()
        data class CustomDns(val dns: String) : State()
        data object Done : State()
    }
}

sealed class VpnCustomDnsScreen {
    data object Default : ActivityParams {
        private fun readResolve(): Any = Default
    }
}
