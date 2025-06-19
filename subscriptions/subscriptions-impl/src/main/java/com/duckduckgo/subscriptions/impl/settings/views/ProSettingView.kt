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
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.api.SubscriptionScreens.RestoreSubscriptionScreenWithParams
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionsSettingsScreenWithEmptyParams
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.GRACE_PERIOD
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.NOT_AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.WAITING
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.databinding.ViewSettingsBinding
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenBuyScreen
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenRestoreScreen
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.Command.OpenSettings
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.ViewState
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.ViewState.SubscriptionRegion.ROW
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingViewModel.ViewState.SubscriptionRegion.US
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsWebViewActivityWithParams
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ViewScope::class)
class ProSettingView @JvmOverloads constructor(
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

    private val binding: ViewSettingsBinding by viewBinding()

    private val viewModel: ProSettingViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[ProSettingViewModel::class.java]
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

        binding.subscriptionSetting.setOnClickListener(null)
        binding.subscriptionSetting.setOnTouchListener(null)
        binding.subscriptionBuy.setOnClickListener(null)
        binding.subscriptionBuy.setOnTouchListener(null)
        binding.subscriptionGet.setOnClickListener(null)
        binding.subscriptionGet.setOnTouchListener(null)
        binding.subscriptionRestore.setOnTouchListener(null)
        binding.subscriptionRestore.setOnClickListener(null)

        binding.subscriptionSettingContainer.setOnClickListener {
            viewModel.onSettings()
        }

        binding.subscriptionRestoreContainer.setOnClickListener {
            viewModel.onRestore()
        }

        binding.subscriptionBuyContainer.setOnClickListener {
            viewModel.onBuy()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        job.cancel()
        conflatedStateJob.cancel()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun renderView(viewState: ViewState) {
        when (viewState.status) {
            AUTO_RENEWABLE, NOT_AUTO_RENEWABLE, GRACE_PERIOD -> {
                with(binding) {
                    subscriptionBuyContainer.isGone = true
                    subscriptionRestoreContainer.isGone = true
                    subscriptionSetting.isGone = true

                    subscribedSubscriptionSetting.isVisible = true
                    subscriptionSettingContainer.isVisible = true
                    subscribedSubscriptionSetting.isVisible = true
                }
            }
            WAITING -> {
                with(binding) {
                    subscriptionBuyContainer.isGone = true
                    subscriptionRestoreContainer.isGone = true
                    subscribedSubscriptionSetting.isGone = true

                    subscriptionSettingContainer.isVisible = true
                    subscriptionSetting.isVisible = true
                    subscriptionSetting.setSecondaryText(context.getString(R.string.subscriptionSettingActivating))
                }
            }
            EXPIRED, INACTIVE -> {
                with(binding) {
                    subscriptionBuyContainer.isGone = true
                    subscriptionRestoreContainer.isGone = true
                    subscribedSubscriptionSetting.isGone = true

                    subscriptionSettingContainer.isVisible = true
                    subscriptionSetting.isVisible = true
                    subscriptionSetting.setSecondaryText(context.getString(R.string.subscriptionSettingExpired))
                    subscriptionSetting.setTrailingIconResource(CommonR.drawable.ic_exclamation_recolorable_16)
                }
            }
            else -> {
                with(binding) {
                    subscriptionBuy.setPrimaryText(context.getString(R.string.subscriptionSettingSubscribe))
                    subscriptionBuy.setSecondaryText(getSubscriptionSecondaryText(viewState))
                    subscriptionGet.setText(
                        when (viewState.freeTrialEligible) {
                            true -> R.string.subscriptionSettingTryFreeTrial
                            false -> R.string.subscriptionSettingGet
                        },
                    )

                    subscriptionBuyContainer.isVisible = true
                    subscriptionRestoreContainer.isVisible = true

                    subscriptionSettingContainer.isGone = true
                }
            }
        }
    }

    private fun getSubscriptionSecondaryText(viewState: ViewState) = if (viewState.duckAiPlusAvailable) {
        when (viewState.region) {
            ROW -> context.getString(R.string.subscriptionSettingSubscribeWithDuckAiSubtitleRow)
            US -> context.getString(R.string.subscriptionSettingSubscribeWithDuckAiSubtitle)
            else -> ""
        }
    } else {
        when (viewState.region) {
            ROW -> context.getString(R.string.subscriptionSettingSubscribeSubtitleRow)
            US -> context.getString(R.string.subscriptionSettingSubscribeSubtitle)
            else -> ""
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
                        origin = "funnel_appsettings_android",
                    ),
                )
            }
            is OpenRestoreScreen -> {
                globalActivityStarter.start(context, RestoreSubscriptionScreenWithParams(isOriginWeb = false))
            }
        }
    }
}
