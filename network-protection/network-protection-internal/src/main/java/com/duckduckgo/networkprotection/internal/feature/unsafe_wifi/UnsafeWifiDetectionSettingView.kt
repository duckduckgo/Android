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

package com.duckduckgo.networkprotection.internal.feature.unsafe_wifi

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build.VERSION_CODES
import android.util.AttributeSet
import android.view.View
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.networkprotection.impl.settings.VpnSettingPlugin
import com.duckduckgo.networkprotection.internal.databinding.VpnViewSettingsUnsafeWifiBinding
import com.duckduckgo.networkprotection.internal.feature.UNSAFE_WIFI_DETECTION_PRIORITY
import com.duckduckgo.networkprotection.internal.feature.unsafe_wifi.UnsafeWifiDetectionSettingView.Event.Init
import com.duckduckgo.networkprotection.internal.feature.unsafe_wifi.UnsafeWifiDetectionSettingView.Event.OnDisableIntent
import com.duckduckgo.networkprotection.internal.feature.unsafe_wifi.UnsafeWifiDetectionSettingView.Event.OnEnableIntent
import com.duckduckgo.networkprotection.internal.feature.unsafe_wifi.UnsafeWifiDetectionSettingView.State.Disable
import com.duckduckgo.networkprotection.internal.feature.unsafe_wifi.UnsafeWifiDetectionSettingView.State.Enable
import com.duckduckgo.networkprotection.internal.feature.unsafe_wifi.UnsafeWifiDetectionSettingView.State.Idle
import com.duckduckgo.networkprotection.internal.feature.unsafe_wifi.UnsafeWifiDetectionViewModel.Factory
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@InjectWith(ViewScope::class)
class UnsafeWifiDetectionSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: Factory

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private var mainCoroutineScope: CoroutineScope? = null

    // we use replay = 0 because we don't want to emit the last value upon subscription
    private val events = MutableSharedFlow<Event>(replay = 1, extraBufferCapacity = 1)

    private val binding: VpnViewSettingsUnsafeWifiBinding by viewBinding()

    private val viewModel: UnsafeWifiDetectionViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[UnsafeWifiDetectionViewModel::class.java]
    }

    private val toggleListener = OnCheckedChangeListener { p0, value ->
        mainCoroutineScope?.launch {
            events.emit(
                if (value) OnEnableIntent else OnDisableIntent,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        @SuppressLint("NoHardcodedCoroutineDispatcher")
        mainCoroutineScope = CoroutineScope(SupervisorJob() + dispatcherProvider.main())

        binding.unsafeWifiDetection.setOnCheckedChangeListener(toggleListener)

        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            events
                .flatMapLatest { viewModel.reduce(it) }
                .flowOn(dispatcherProvider.io())
                .onStart { events.emit(Init) }
                .collect(::render)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainCoroutineScope?.cancel()
        mainCoroutineScope = null
    }

    private fun render(state: State) {
        when (state) {
            Disable -> binding.unsafeWifiDetection.quietlySetIsChecked(false, toggleListener)
            Enable -> binding.unsafeWifiDetection.quietlySetIsChecked(true, toggleListener)
            Idle -> {
                /* no-op */
            }
        }
    }

    sealed class Event {
        data object Init : Event()
        data object OnEnableIntent : Event()
        data object OnDisableIntent : Event()
    }

    sealed class State {
        data object Idle : State()
        data object Enable : State()
        data object Disable : State()
    }
}

@ContributesMultibinding(ActivityScope::class)
@PriorityKey(UNSAFE_WIFI_DETECTION_PRIORITY)
class UnsafeWifiDetectionSettingViewPlugin @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : VpnSettingPlugin {
    override fun getView(context: Context): View? {
        return if (appBuildConfig.sdkInt < VERSION_CODES.S) {
            null
        } else {
            UnsafeWifiDetectionSettingView(context)
        }
    }
}
