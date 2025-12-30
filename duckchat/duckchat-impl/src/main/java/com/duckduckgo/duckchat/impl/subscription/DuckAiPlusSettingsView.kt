/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.subscription

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
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.impl.databinding.ViewRevengeAiSettingsBinding
import com.duckduckgo.duckchat.impl.subscription.RevengeAIPlusSettingsViewModel.Command
import com.duckduckgo.duckchat.impl.subscription.RevengeAIPlusSettingsViewModel.Command.OpenRevengeAIPlusSettings
import com.duckduckgo.duckchat.impl.subscription.RevengeAIPlusSettingsViewModel.ViewState
import com.duckduckgo.duckchat.impl.subscription.RevengeAIPlusSettingsViewModel.ViewState.SettingState
import com.duckduckgo.navigation.api.GlobalActivityStarter
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.duckchat.impl.R as DuckChatR

@InjectWith(ViewScope::class)
class RevengeAIPlusSettingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val binding: ViewRevengeAiSettingsBinding by viewBinding()

    private val viewModel: RevengeAIPlusSettingsViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[RevengeAIPlusSettingsViewModel::class.java]
    }

    private var job: ConflatedJob = ConflatedJob()
    private var conflatedStateJob: ConflatedJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)
        val coroutineScope = findViewTreeLifecycleOwner()?.lifecycleScope

        job += viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)

        conflatedStateJob += viewModel.viewState
            .onEach { renderView(it) }
            .launchIn(coroutineScope!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        job.cancel()
        conflatedStateJob.cancel()
    }

    private fun renderView(viewState: ViewState) {
        with(binding.revengeAiSettings) {
            when (viewState.settingState) {
                is SettingState.Enabled -> {
                    this.isVisible = true
                    setStatus(isOn = viewState.isRevengeAIEnabled)
                    if (viewState.isRevengeAIPaidSettingsFeatureEnabled) {
                        setLeadingIconResource(DuckChatR.drawable.ic_duckduckgo_ai_color_24)
                    } else {
                        setLeadingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_ai_chat_color_24)
                    }
                    this.isClickable = true
                    setClickListener { viewModel.onRevengeAIClicked() }
                }
                SettingState.Disabled -> {
                    this.isVisible = true
                    this.isClickable = false
                    setStatus(isOn = false)
                    setClickListener(null)
                    if (viewState.isRevengeAIPaidSettingsFeatureEnabled) {
                        setLeadingIconResource(DuckChatR.drawable.ic_duckduckgo_ai_grayscale_color_24)
                    } else {
                        setLeadingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_ai_chat_grayscale_color_24)
                    }
                }
                SettingState.Hidden -> this.isGone = true
            }
        }
    }

    private fun processCommands(command: Command) {
        when (command) {
            is OpenRevengeAIPlusSettings -> {
                globalActivityStarter.start(context, RevengeAIPaidSettingsNoParams)
            }
        }
    }
}
