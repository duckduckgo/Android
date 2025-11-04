/*
 * Copyright (c) 2023 DuckDuckGo
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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.pir.api.dashboard.PirDashboardWebViewScreen
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.ViewPirSettingsBinding
import com.duckduckgo.subscriptions.impl.pir.PirActivity.Companion.PirScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.Command
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.Command.OpenPirDashboard
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.Command.OpenPirDesktop
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.ViewState
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.ViewState.PirState.Disabled
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.ViewState.PirState.Enabled
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingViewModel.ViewState.PirState.Hidden
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ViewScope::class)
class PirSettingView @JvmOverloads constructor(
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

    private val binding: ViewPirSettingsBinding by viewBinding()

    private val viewModel: PirSettingViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[PirSettingViewModel::class.java]
    }

    private var job: ConflatedJob = ConflatedJob()
    private val conflatedStateJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        job += viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)

        conflatedStateJob += viewModel.viewState
            .onEach { renderView(it) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        job.cancel()
        conflatedStateJob.cancel()
    }

    private fun renderView(viewState: ViewState) {
        with(binding.pirSettings) {
            when (viewState.pirState) {
                is Enabled -> {
                    isVisible = true
                    setStatus(isOn = true)
                    setLeadingIconResource(R.drawable.ic_identity_blocked_pir_color_24)
                    isClickable = true
                    binding.pirSettings.setClickListener { viewModel.onPir(viewState.pirState.type) }
                }

                is Disabled -> {
                    isVisible = true
                    isClickable = false
                    setStatus(isOn = false)
                    binding.pirSettings.setClickListener(null)
                    setLeadingIconResource(R.drawable.ic_identity_blocked_pir_grayscale_color_24)
                }

                Hidden -> isGone = true
            }
        }
    }

    private fun processCommands(command: Command) {
        when (command) {
            is OpenPirDesktop -> {
                globalActivityStarter.start(context, PirScreenWithEmptyParams)
            }

            OpenPirDashboard -> {
                globalActivityStarter.start(context, PirDashboardWebViewScreen)
            }

            Command.ShowPirStorageUnavailableDialog -> {
                TextAlertDialogBuilder(context)
                    .setTitle(R.string.pirStorageUnavailableDialogTitle)
                    .setMessage(R.string.pirStorageUnavailableDialogMessage)
                    .setPositiveButton(R.string.pirStorageUnavailableDialogButton, GHOST)
                    .show()
            }
        }
    }
}
