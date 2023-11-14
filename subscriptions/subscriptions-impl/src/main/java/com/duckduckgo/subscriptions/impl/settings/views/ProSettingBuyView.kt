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

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.impl.databinding.ViewSettingsBuyBinding
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingBuyViewModel.Command
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingBuyViewModel.Command.OpenBuyScreen
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingBuyViewModel.Factory
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsActivity.Companion.SubscriptionsScreenWithEmptyParams
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ViewScope::class)
class ProSettingBuyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: Factory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private var coroutineScope: CoroutineScope? = null

    private val binding: ViewSettingsBuyBinding by viewBinding()

    private val viewModel: ProSettingBuyViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[ProSettingBuyViewModel::class.java]
    }

    private var job: ConflatedJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.buy.setClickListener {
            viewModel.onBuyClicked()
        }

        @SuppressLint("NoHardcodedCoroutineDispatcher")
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        job += viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        coroutineScope?.cancel()
        job.cancel()
        coroutineScope = null
    }

    private fun processCommands(command: Command) {
        when (command) {
            is OpenBuyScreen -> {
                globalActivityStarter.start(context, SubscriptionsScreenWithEmptyParams)
            }
        }
    }
}
