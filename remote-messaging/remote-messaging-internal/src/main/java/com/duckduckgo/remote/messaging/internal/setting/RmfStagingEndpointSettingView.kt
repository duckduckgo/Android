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

package com.duckduckgo.remote.messaging.internal.setting

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.CompoundButton
import android.widget.FrameLayout
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.remote.messaging.internal.feature.RmfSettingPlugin
import com.duckduckgo.remotemessaging.internal.databinding.RmfSimpleViewBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ViewScope::class)
class RmfStagingEndpointSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var rmfInternalSettings: RmfInternalSettings

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    private val toggleListener = CompoundButton.OnCheckedChangeListener { _, value ->
        appCoroutineScope.launch(dispatcherProvider.io()) {
            rmfInternalSettings.useStatingEndpoint().setRawStoredState(State(enable = value))
        }
    }

    private val binding: RmfSimpleViewBinding by viewBinding()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.root.showSwitch()
        binding.root.setPrimaryText("Use RMF staging endpoint")
        binding.root.setSecondaryText("Enable to use RMF staging endpoint")

        appCoroutineScope.launch(dispatcherProvider.io()) {
            binding.root.quietlySetIsChecked(rmfInternalSettings.useStatingEndpoint().isEnabled(), toggleListener)
        }
    }
}

@ContributesMultibinding(ActivityScope::class)
class RecoverSubscriptionViewPlugin @Inject constructor() : RmfSettingPlugin {
    override fun getView(context: Context): View {
        return RmfStagingEndpointSettingView(context)
    }
}
