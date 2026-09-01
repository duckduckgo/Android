/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.settings.views

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.subscriptions.impl.databinding.ViewPartnerBenefitSettingsBinding
import com.duckduckgo.subscriptions.impl.settings.views.PartnerBenefitSettingViewModel.Command
import com.duckduckgo.subscriptions.impl.settings.views.PartnerBenefitSettingViewModel.Command.OpenPartnershipsHub
import com.duckduckgo.subscriptions.impl.settings.views.PartnerBenefitSettingViewModel.ViewState
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ViewScope::class)
class PartnerBenefitSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var browserNav: BrowserNav

    private val binding: ViewPartnerBenefitSettingsBinding by viewBinding()

    private val viewModel: PartnerBenefitSettingViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[PartnerBenefitSettingViewModel::class.java]
    }

    private val commandJob = ConflatedJob()
    private val stateJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        val lifecycleOwner = findViewTreeLifecycleOwner() ?: return
        lifecycleOwner.lifecycle.addObserver(viewModel)

        binding.partnerBenefitSettings.setClickListener { viewModel.onPartnershipsHubClicked() }

        commandJob += viewModel.commands()
            .onEach { processCommand(it) }
            .launchIn(lifecycleOwner.lifecycleScope)

        stateJob += viewModel.viewState
            .onEach { renderView(it) }
            .launchIn(lifecycleOwner.lifecycleScope)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        commandJob.cancel()
        stateJob.cancel()
    }

    private fun renderView(viewState: ViewState) {
        binding.partnerBenefitSettings.isVisible = viewState.isVisible
    }

    private fun processCommand(command: Command) {
        when (command) {
            is OpenPartnershipsHub -> context.startActivity(browserNav.openInNewTab(context, command.url))
        }
    }
}
