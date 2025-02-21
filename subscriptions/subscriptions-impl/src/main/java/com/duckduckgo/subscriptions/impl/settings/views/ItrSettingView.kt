/*
 * Copyright (c) 2024 DuckDuckGo
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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.settings.SettingNodeView
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants
import com.duckduckgo.subscriptions.impl.databinding.ViewItrSettingsBinding
import com.duckduckgo.subscriptions.impl.settings.views.ItrSettingViewModel.Command
import com.duckduckgo.subscriptions.impl.settings.views.ItrSettingViewModel.Command.OpenItr
import com.duckduckgo.subscriptions.impl.settings.views.ItrSettingViewModel.ViewState
import com.duckduckgo.subscriptions.impl.settings.views.ItrSettingViewModel.ViewState.ItrState
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsWebViewActivityWithParams
import dagger.android.support.AndroidSupportInjection
import java.util.UUID
import javax.inject.Inject

@SuppressLint("ViewConstructor")
@InjectWith(ViewScope::class)
class ItrSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    searchableId: UUID,
) : SettingNodeView<Command, ViewState, ItrSettingViewModel>(context, attrs, defStyle, searchableId) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val binding: ViewItrSettingsBinding by viewBinding()

    override fun provideViewModel(): ItrSettingViewModel {
        return ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[ItrSettingViewModel::class.java]
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
    }

    override fun renderView(viewState: ViewState) {
        with(binding.itrSettings) {
            when (viewState.itrState) {
                is ItrState.Enabled -> {
                    isVisible = true
                    setStatus(isOn = true)
                    setLeadingIconResource(R.drawable.ic_identity_theft_restoration_color_24)
                    isClickable = true
                    setClickListener { viewModel.onItr() }
                }
                ItrState.Disabled -> {
                    isVisible = true
                    isClickable = false
                    setStatus(isOn = false)
                    setClickListener(null)
                    setLeadingIconResource(R.drawable.ic_identity_theft_restoration_grayscale_color_24)
                }
                ItrState.Hidden -> isGone = true
            }
        }
    }

    override fun processCommands(command: Command) {
        when (command) {
            is OpenItr -> {
                globalActivityStarter.start(
                    context,
                    SubscriptionsWebViewActivityWithParams(
                        url = SubscriptionsConstants.ITR_URL,
                    ),
                )
            }
        }
    }
}
