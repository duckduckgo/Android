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
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.databinding.ViewSettingsBinding
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenBuyScreen
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenSettings
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Factory
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.ViewState
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsActivity.Companion.SubscriptionsSettingsScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsWebViewActivityWithParams
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ViewScope::class)
class ProSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: Factory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private var coroutineScope: CoroutineScope? = null

    private val binding: ViewSettingsBinding by viewBinding()

    private val viewModel: ProSettingViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[ProSettingViewModel::class.java]
    }

    private var job: ConflatedJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        ViewTreeLifecycleOwner.get(this)?.lifecycle?.addObserver(viewModel)

        @SuppressLint("NoHardcodedCoroutineDispatcher")
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        job += viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)

        viewModel.viewState
            .onEach { renderView(it) }
            .launchIn(coroutineScope!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ViewTreeLifecycleOwner.get(this)?.lifecycle?.removeObserver(viewModel)
        coroutineScope?.cancel()
        job.cancel()
        coroutineScope = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun renderView(viewState: ViewState) {
        binding.subscriptionSetting.setOnClickListener(null)
        binding.subscriptionSetting.setOnTouchListener(null)
        binding.subscriptionBuy.setOnClickListener(null)
        binding.subscriptionBuy.setOnTouchListener(null)

        if (viewState.hasSubscription) {
            binding.subscriptionBuy.gone()
            binding.subscribeSecondary.gone()
            binding.subscriptionSetting.show()
            binding.settingContainer.setOnClickListener {
                viewModel.onSettings()
            }
        } else {
            val htmlText = context.getString(R.string.subscriptionSettingFeaturesList).html(context)
            binding.subscribeSecondary.show()
            binding.subscribeSecondary.text = htmlText
            binding.subscriptionBuy.show()
            binding.subscriptionSetting.gone()
            binding.settingContainer.setOnClickListener {
                viewModel.onBuy()
            }
        }
    }

    private fun processCommands(command: Command) {
        when (command) {
            is OpenSettings -> {
                globalActivityStarter.start(context, SubscriptionsSettingsScreenWithEmptyParams)
            }
            is OpenBuyScreen -> {
                globalActivityStarter.start(
                    context,
                    SubscriptionsWebViewActivityWithParams(
                        url = SubscriptionsConstants.BUY_URL,
                        screenTitle = "",
                        defaultToolbar = true,
                    ),
                )
            }
        }
    }
}

class SubscriptionSettingLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return true
    }
}
