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

package com.duckduckgo.pir.impl.freemium.settings

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
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.pir.api.PirScreens.PirDashboardWebViewScreen
import com.duckduckgo.pir.impl.R
import com.duckduckgo.pir.impl.databinding.ViewPirFreemiumSettingsBinding
import com.duckduckgo.pir.impl.freemium.PirFreemiumState.ELIGIBLE
import com.duckduckgo.pir.impl.freemium.PirFreemiumState.NOT_ELIGIBLE
import com.duckduckgo.pir.impl.freemium.PirFreemiumState.USED
import com.duckduckgo.pir.impl.freemium.settings.PirFreemiumSettingViewModel.Command
import com.duckduckgo.pir.impl.freemium.settings.PirFreemiumSettingViewModel.Command.OpenPirDashboard
import com.duckduckgo.pir.impl.freemium.settings.PirFreemiumSettingViewModel.ViewState
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ViewScope::class)
class PirFreemiumSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val binding: ViewPirFreemiumSettingsBinding by viewBinding()

    private val viewModel: PirFreemiumSettingViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[PirFreemiumSettingViewModel::class.java]
    }

    private val job = ConflatedJob()
    private val conflatedStateJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        val coroutineScope = findViewTreeLifecycleOwner()?.lifecycleScope!!

        job += viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope)

        conflatedStateJob += viewModel.viewState
            .onEach { renderView(it) }
            .launchIn(coroutineScope)

        binding.pirFreemiumPromo.isClickable = true
        binding.pirFreemiumContainer.setOnClickListener { viewModel.onEntryPointClicked() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        job.cancel()
        conflatedStateJob.cancel()
    }

    private fun renderView(viewState: ViewState) {
        when (viewState.freemiumState) {
            NOT_ELIGIBLE -> binding.pirFreemiumContainer.isGone = true

            ELIGIBLE -> {
                binding.pirFreemiumContainer.isVisible = true
                binding.pirFreemiumCta.setText(R.string.pirFreemiumSettingStartScan)
            }

            USED -> {
                binding.pirFreemiumContainer.isVisible = true
                binding.pirFreemiumCta.setText(R.string.pirFreemiumSettingViewResults)
            }
        }
    }

    private fun processCommands(command: Command) {
        when (command) {
            OpenPirDashboard -> globalActivityStarter.start(context, PirDashboardWebViewScreen)
        }
    }
}
