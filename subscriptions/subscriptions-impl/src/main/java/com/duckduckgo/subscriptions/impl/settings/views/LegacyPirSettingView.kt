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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.impl.databinding.LegacyViewPirSettingsBinding
import com.duckduckgo.subscriptions.impl.pir.PirActivity.Companion.PirScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.settings.views.LegacyPirSettingViewModel.Command
import com.duckduckgo.subscriptions.impl.settings.views.LegacyPirSettingViewModel.Command.OpenPir
import com.duckduckgo.subscriptions.impl.settings.views.LegacyPirSettingViewModel.ViewState
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ViewScope::class)
class LegacyPirSettingView @JvmOverloads constructor(
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

    private var coroutineScope: CoroutineScope? = null

    private val binding: LegacyViewPirSettingsBinding by viewBinding()

    private val viewModel: LegacyPirSettingViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[LegacyPirSettingViewModel::class.java]
    }

    private var job: ConflatedJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        binding.pirSettings.setClickListener {
            viewModel.onPir()
        }

        coroutineScope = CoroutineScope(SupervisorJob() + dispatchers.main())

        job += viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)

        viewModel.viewState
            .onEach { renderView(it) }
            .launchIn(coroutineScope!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        coroutineScope?.cancel()
        job.cancel()
        coroutineScope = null
    }

    private fun renderView(viewState: ViewState) {
        if (viewState.hasSubscription) {
            binding.pirSettings.show()
        } else {
            binding.pirSettings.gone()
        }
    }

    private fun processCommands(command: Command) {
        when (command) {
            is OpenPir -> {
                globalActivityStarter.start(context, PirScreenWithEmptyParams)
            }
        }
    }
}
