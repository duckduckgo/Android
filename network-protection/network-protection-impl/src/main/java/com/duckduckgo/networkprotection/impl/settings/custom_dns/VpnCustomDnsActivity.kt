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

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.quietlySetIsChecked
import com.duckduckgo.common.ui.view.setEnabledOpacity
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.isPrivateDnsStrict
import com.duckduckgo.common.utils.extensions.launchSettings
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.ActivityNetpCustomDnsBinding
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.CustomDnsEntered
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.CustomDnsSelected
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.DefaultDnsSelected
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.ForceApplyIfReset
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.Init
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.OnApply
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.OnBlockMalwareDisabled
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.Event.OnBlockMalwareEnabled
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.State.CustomDns
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.State.DefaultDns
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.State.Done
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsScreen.Default
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(Default::class)
class VpnCustomDnsActivity : DuckDuckGoActivity() {

    private val binding: ActivityNetpCustomDnsBinding by viewBinding()
    private val viewModel: VpnCustomDnsViewModel by bindViewModel()

    private val events = MutableSharedFlow<Event>(replay = 1, extraBufferCapacity = 1)

    private val defaultDnsListener = OnCheckedChangeListener { _, value ->
        if (value) {
            lifecycleScope.launch {
                events.emit(DefaultDnsSelected)
            }
        }
    }

    private val customDnsListener = OnCheckedChangeListener { _, value ->
        if (value) {
            lifecycleScope.launch {
                events.emit(CustomDnsSelected)
            }
        }
    }

    private val customDnsTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(
            p0: CharSequence?,
            p1: Int,
            p2: Int,
            p3: Int,
        ) {
        }

        override fun onTextChanged(
            p0: CharSequence?,
            p1: Int,
            p2: Int,
            p3: Int,
        ) {
        }

        override fun afterTextChanged(p0: Editable?) {
            lifecycleScope.launch {
                events.emit(CustomDnsEntered(p0.toString()))
            }
        }
    }

    private val blockMalwareToggleListener = OnCheckedChangeListener { _, value ->
        lifecycleScope.launch {
            if (value) {
                events.emit(OnBlockMalwareEnabled)
            } else {
                events.emit(OnBlockMalwareDisabled)
            }
        }
    }

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var networkProtectionState: NetworkProtectionState

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
                .onStart { events.emit(Init(this@VpnCustomDnsActivity.isPrivateDnsStrict())) }
                .collect(::render)
        }
        binding.defaultDnsOption.setOnCheckedChangeListener(defaultDnsListener)
        binding.customDnsOption.setOnCheckedChangeListener(customDnsListener)
        binding.customDns.addTextChangedListener(customDnsTextWatcher)
        binding.applyDnsChanges.setOnClickListener {
            lifecycleScope.launch {
                events.emit(OnApply)
            }
        }
    }

    private fun render(state: State) {
        when (state) {
            is DefaultDns -> {
                handleAllowChange(state.allowChange)

                binding.defaultDnsOption.quietlySetIsChecked(true, defaultDnsListener)
                binding.defaultDnsDescription.isEnabled = true
                binding.defaultDnsDescription.show()

                binding.customDns.removeTextChangedListener(customDnsTextWatcher)
                binding.customDns.isEditable = false
                binding.customDnsSection.gone()

                if (state.allowBlockMalware) {
                    binding.blockMalwareSection.show()
                    binding.blockMalwareToggle.quietlySetIsChecked(state.blockMalware, blockMalwareToggleListener)
                } else {
                    binding.blockMalwareSection.gone()
                }
            }

            is CustomDns -> {
                handleAllowChange(state.allowChange)
                binding.defaultDnsDescription.gone()

                binding.customDnsOption.quietlySetIsChecked(true, customDnsListener)
                binding.customDnsSection.show()
                binding.blockMalwareSection.gone()
                binding.customDns.removeTextChangedListener(customDnsTextWatcher)
                state.dns?.also {
                    binding.customDns.text = it
                }
                binding.customDns.addTextChangedListener(customDnsTextWatcher)
                binding.applyDnsChanges.isEnabled = state.applyEnabled
            }

            is Done -> {
                networkProtectionState.restart()
                if (state.finish) {
                    finish()
                }
            }
        }
    }

    private fun handleAllowChange(allowChange: Boolean) {
        if (allowChange) {
            binding.dnsWarning.gone()

            binding.defaultDnsOption.enable()
            binding.defaultDnsDescription.enable()
            binding.customDnsOption.enable()
            binding.customDnsWarning.enable()
            binding.customDns.enable()
            binding.customDns.isEditable = true
            binding.customDnsSectionHeader.enable()

            binding.blockMalwareToggle.enable()
        } else {
            binding.dnsWarning.show()
            binding.dnsWarning.setClickableLink(
                "open_settings_link",
                getText(R.string.netpCustomDnsPrivateDnsWarning),
            ) {
                this@VpnCustomDnsActivity.launchSettings()
            }
            binding.defaultDnsOption.disable()
            binding.defaultDnsDescription.disable()

            binding.customDnsOption.disable()
            binding.customDnsWarning.disable()
            binding.customDns.disable()
            binding.customDns.removeTextChangedListener(customDnsTextWatcher)
            binding.customDns.isEditable = false
            binding.customDnsSectionHeader.disable()

            binding.blockMalwareToggle.disable()
        }
    }

    private fun View.enable() {
        this.isEnabled = true
        this.setEnabledOpacity(true)
    }

    private fun View.disable() {
        this.isEnabled = false
        this.setEnabledOpacity(false)
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch {
            events.emit(ForceApplyIfReset)
        }
    }

    internal sealed class Event {
        data class Init(val isPrivateDnsActive: Boolean) : Event()
        data class CustomDnsEntered(val dns: String?) : Event()
        data object CustomDnsSelected : Event()
        data object DefaultDnsSelected : Event()
        data object OnApply : Event()
        data object ForceApplyIfReset : Event()
        data object OnBlockMalwareEnabled : Event()
        data object OnBlockMalwareDisabled : Event()
    }

    internal sealed class State {
        data class DefaultDns(
            val allowChange: Boolean,
            val blockMalware: Boolean,
            val allowBlockMalware: Boolean,
        ) : State()

        data class CustomDns(
            val dns: String?,
            val allowChange: Boolean,
            val applyEnabled: Boolean,
        ) : State()

        data class Done(val finish: Boolean = true) : State()
    }
}

sealed class VpnCustomDnsScreen {
    data object Default : ActivityParams {
        private fun readResolve(): Any = Default
    }
}
