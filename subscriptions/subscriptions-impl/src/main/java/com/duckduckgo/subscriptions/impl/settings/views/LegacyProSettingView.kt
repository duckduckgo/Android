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
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.listitem.CheckListItem.CheckItemStatus.ALERT
import com.duckduckgo.common.ui.view.listitem.CheckListItem.CheckItemStatus.DISABLED
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.api.SubscriptionStatus.GRACE_PERIOD
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.NOT_AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.WAITING
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.databinding.LegacyViewSettingsBinding
import com.duckduckgo.subscriptions.impl.settings.views.LegacyProSettingViewModel.Command
import com.duckduckgo.subscriptions.impl.settings.views.LegacyProSettingViewModel.Command.OpenBuyScreen
import com.duckduckgo.subscriptions.impl.settings.views.LegacyProSettingViewModel.Command.OpenRestoreScreen
import com.duckduckgo.subscriptions.impl.settings.views.LegacyProSettingViewModel.Command.OpenSettings
import com.duckduckgo.subscriptions.impl.settings.views.LegacyProSettingViewModel.ViewState
import com.duckduckgo.subscriptions.impl.settings.views.LegacyProSettingViewModel.ViewState.SubscriptionRegion.ROW
import com.duckduckgo.subscriptions.impl.settings.views.LegacyProSettingViewModel.ViewState.SubscriptionRegion.US
import com.duckduckgo.subscriptions.impl.ui.RestoreSubscriptionActivity.Companion.RestoreSubscriptionScreenWithParams
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
class LegacyProSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private var coroutineScope: CoroutineScope? = null

    private val binding: LegacyViewSettingsBinding by viewBinding()

    private val viewModel: LegacyProSettingViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[LegacyProSettingViewModel::class.java]
    }

    private var job: ConflatedJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        @SuppressLint("NoHardcodedCoroutineDispatcher")
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        job += viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)

        viewModel.viewState
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
        coroutineScope?.cancel()
        job.cancel()
        coroutineScope = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun renderView(viewState: ViewState) {
        when (viewState.status) {
            AUTO_RENEWABLE, NOT_AUTO_RENEWABLE, GRACE_PERIOD -> {
                binding.subscriptionBuyContainer.gone()
                binding.subscriptionRestoreContainer.gone()
                binding.subscriptionWaitingContainer.gone()
                binding.subscriptionSettingContainer.show()
            }
            WAITING -> {
                binding.subscriptionBuyContainer.gone()
                binding.subscriptionWaitingContainer.show()
                binding.subscriptionSettingContainer.gone()
                binding.subscriptionRestoreContainer.show()
            }
            EXPIRED, INACTIVE -> {
                binding.subscriptionBuy.setPrimaryText(context.getString(R.string.subscriptionSettingExpired))
                binding.subscriptionBuy.setSecondaryText(context.getString(R.string.subscriptionSettingExpiredSubtitle))
                binding.subscriptionBuy.setItemStatus(ALERT)
                binding.subscriptionGet.setText(R.string.subscriptionSettingExpiredViewPlans)
                binding.subscriptionBuyContainer.show()
                binding.subscriptionSettingContainer.show()
                binding.subscriptionWaitingContainer.gone()
                binding.subscriptionRestoreContainer.gone()
            }
            else -> {
                binding.subscriptionBuy.setPrimaryText(context.getString(R.string.subscriptionSettingSubscribe))
                binding.subscriptionBuy.setSecondaryText(
                    when (viewState.region) {
                        ROW -> context.getString(R.string.subscriptionSettingSubscribeSubtitleRow)
                        US -> context.getString(R.string.subscriptionSettingSubscribeSubtitle)
                        else -> ""
                    },
                )
                binding.subscriptionBuy.setItemStatus(DISABLED)
                binding.subscriptionGet.setText(R.string.subscriptionSettingGet)
                binding.subscriptionBuyContainer.show()
                binding.subscriptionSettingContainer.gone()
                binding.subscriptionWaitingContainer.gone()
                binding.subscriptionRestoreContainer.show()
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
                    ),
                )
            }
            is OpenRestoreScreen -> {
                globalActivityStarter.start(context, RestoreSubscriptionScreenWithParams(isOriginWeb = false))
            }
        }
    }
}
