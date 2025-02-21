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
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.ui.settings.SettingNodeView
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.navigation.api.GlobalActivityStarter
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
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionActivity.Companion.RestoreSubscriptionScreenWithParams
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsActivity.Companion.SubscriptionsSettingsScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsWebViewActivityWithParams
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.UUID

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class ProSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    searchableId: UUID,
) : SettingNodeView<Command, ViewState, ProSettingViewModel>(context, attrs, defStyle, searchableId) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val binding: ViewSettingsBinding by viewBinding()

    override fun provideViewModel(): ProSettingViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[ProSettingViewModel::class.java]
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

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

    @SuppressLint("ClickableViewAccessibility")
    override fun renderView(viewState: ViewState) {
        if (viewState.visible) {
            binding.root.show()
            // TODO send "m_privacy-pro_is-enabled" pixel
        } else {
            binding.root.gone()
        }
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
                    subscriptionSetting.setTrailingIconResource(CommonR.drawable.ic_exclamation_red_16)
                }
            }
            else -> {
                with(binding) {
                    subscriptionBuy.setPrimaryText(context.getString(R.string.subscriptionSettingSubscribe))
                    subscriptionBuy.setSecondaryText(
                        when (viewState.region) {
                            ROW -> context.getString(R.string.subscriptionSettingSubscribeSubtitleRow)
                            US -> context.getString(R.string.subscriptionSettingSubscribeSubtitle)
                            else -> ""
                        },
                    )
                    subscriptionGet.setText(R.string.subscriptionSettingGet)

                    subscriptionBuyContainer.isVisible = true
                    subscriptionRestoreContainer.isVisible = true

                    subscriptionSettingContainer.isGone = true
                }
            }
        }
    }

    override fun processCommands(command: Command) {
        when (command) {
            is OpenSettings -> {
                globalActivityStarter.start(context, SubscriptionsSettingsScreenWithEmptyParams)
            }
            is OpenBuyScreen -> {
                globalActivityStarter.start(
                    context,
                    SubscriptionsWebViewActivityWithParams(
                        url = SubscriptionsConstants.BUY_URL,
                    ),
                )
            }
            is OpenRestoreScreen -> {
                globalActivityStarter.start(context, RestoreSubscriptionScreenWithParams(isOriginWeb = false))
            }
        }
    }
}
