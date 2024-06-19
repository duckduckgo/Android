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
import android.text.Annotation
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.SpannedString
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.quietlySetIsChecked
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.isPrivateDnsActive
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
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.State.CustomDns
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.State.DefaultDns
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.State.Done
import com.duckduckgo.networkprotection.impl.settings.custom_dns.VpnCustomDnsActivity.State.NeedApply
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
                .onStart { events.emit(Init(this@VpnCustomDnsActivity.isPrivateDnsActive())) }
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
                binding.defaultDnsOption.quietlySetIsChecked(true, defaultDnsListener)
                binding.customDnsOption.isEnabled = state.allowCustom
                if (state.allowCustom) {
                    binding.customDnsOption.isEnabled = true
                    binding.defaultDnsDescription.show()
                    binding.defaultDnsDescription.text = getString(R.string.netpDdgDnsByLine)
                    binding.customDnsOption.setTextColor(getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorPrimaryText))
                } else {
                    binding.customDnsOption.isEnabled = false
                    binding.defaultDnsDescription.show()
                    binding.defaultDnsDescription.text = getString(R.string.netpCustomDnsWarning)
                    binding.defaultDnsDescription.apply {
                        text = addClickableLinks()
                        movementMethod = LinkMovementMethod.getInstance()
                    }
                    binding.customDnsOption.setTextColor(getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorTextDisabled))
                }
                binding.customDns.removeTextChangedListener(customDnsTextWatcher)
                binding.customDns.isEditable = false
                binding.customDns.addTextChangedListener(customDnsTextWatcher)
                binding.customDnsSection.gone()
            }

            is CustomDns -> {
                binding.defaultDnsDescription.gone()
                binding.customDnsOption.isEnabled = true
                binding.customDnsOption.quietlySetIsChecked(true, customDnsListener)
                binding.customDns.removeTextChangedListener(customDnsTextWatcher)
                state.dns?.also {
                    binding.customDns.text = it
                }
                binding.customDns.isEditable = true
                binding.customDns.addTextChangedListener(customDnsTextWatcher)
                binding.customDnsSection.show()
            }

            is NeedApply -> binding.applyDnsChanges.isEnabled = state.value
            Done -> {
                networkProtectionState.restart()
                finish()
            }
        }
    }

    private fun addClickableLinks(): SpannableString {
        val fullText = getText(R.string.netpCustomDnsPrivateDnsWarning) as SpannedString
        val spannableString = SpannableString(fullText)
        val annotations = fullText.getSpans(0, fullText.length, Annotation::class.java)

        annotations?.find { it.value == "open_settings_link" }?.let {
            addSpannable(
                spannableString,
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        this@VpnCustomDnsActivity.launchSettings()
                    }
                },
                fullText,
                it,
            )
        }

        return spannableString
    }

    private fun addSpannable(
        spannableString: SpannableString,
        clickableSpan: ClickableSpan,
        fullText: SpannedString,
        it: Annotation,
    ) {
        spannableString.apply {
            setSpan(
                clickableSpan,
                fullText.getSpanStart(it),
                fullText.getSpanEnd(it),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setSpan(
                UnderlineSpan(),
                fullText.getSpanStart(it),
                fullText.getSpanEnd(it),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setSpan(
                ForegroundColorSpan(
                    getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorAccentBlue),
                ),
                fullText.getSpanStart(it),
                fullText.getSpanEnd(it),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
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
    }

    internal sealed class State {
        data class NeedApply(val value: Boolean) : State()
        data class DefaultDns(val allowCustom: Boolean) : State()
        data class CustomDns(val dns: String?) : State()
        data object Done : State()
    }
}

sealed class VpnCustomDnsScreen {
    data object Default : ActivityParams {
        private fun readResolve(): Any = Default
    }
}
