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

package com.duckduckgo.mobile.android.vpn.ui.newtab

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.mobile.android.vpn.databinding.ViewApptpSettingsItemBinding
import com.duckduckgo.mobile.android.vpn.feature.removal.VpnFeatureRemover
import com.duckduckgo.mobile.android.vpn.ui.newtab.AppTrackingProtectionNewTabSettingsViewModel.ViewState
import com.duckduckgo.mobile.android.vpn.ui.onboarding.VpnStore
import com.duckduckgo.newtabpage.api.NewTabPageSection
import com.duckduckgo.newtabpage.api.NewTabPageSectionSettingsPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ViewScope::class)
class AppTrackingProtectionNewTabSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val binding: ViewApptpSettingsItemBinding by viewBinding()

    private val conflatedJob = ConflatedJob()

    private val viewModel: AppTrackingProtectionNewTabSettingsViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[AppTrackingProtectionNewTabSettingsViewModel::class.java]
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        conflatedJob += viewModel.viewState
            .onEach { render(it) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)
    }

    override fun onDetachedFromWindow() {
        conflatedJob.cancel()
        super.onDetachedFromWindow()
    }

    private fun render(viewState: ViewState) {
        binding.root.quietlySetIsChecked(viewState.enabled) { _, enabled ->
            viewModel.onSettingEnabled(enabled)
        }
    }
}

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(NewTabPageSectionSettingsPlugin.APP_TRACKING_PROTECTION)
class AppTrackingProtectionNewTabSettingViewPlugin @Inject constructor(
    private val vpnStore: VpnStore,
    private val vpnFeatureRemover: VpnFeatureRemover,
) : NewTabPageSectionSettingsPlugin {
    override val name = NewTabPageSection.APP_TRACKING_PROTECTION.name

    override fun getView(context: Context): View {
        return AppTrackingProtectionNewTabSettingView(context)
    }

    override suspend fun isActive(): Boolean {
        if (vpnFeatureRemover.isFeatureRemoved()) {
            return false
        }
        return vpnStore.didShowOnboarding()
    }
}

/**
 * Local feature/settings - they will never be in remote config
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "newTabAppTPSectionSetting",
)
interface NewTabAppTrackingProtectionSectionSetting {
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle
}
