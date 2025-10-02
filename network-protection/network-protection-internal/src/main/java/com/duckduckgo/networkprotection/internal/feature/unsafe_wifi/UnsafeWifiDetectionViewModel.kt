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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.networkprotection.internal.feature.unsafe_wifi.UnsafeWifiDetectionSettingView.Event
import com.duckduckgo.networkprotection.internal.feature.unsafe_wifi.UnsafeWifiDetectionSettingView.Event.Init
import com.duckduckgo.networkprotection.internal.feature.unsafe_wifi.UnsafeWifiDetectionSettingView.Event.OnDisableIntent
import com.duckduckgo.networkprotection.internal.feature.unsafe_wifi.UnsafeWifiDetectionSettingView.Event.OnEnableIntent
import com.duckduckgo.networkprotection.internal.feature.unsafe_wifi.UnsafeWifiDetectionSettingView.State
import com.duckduckgo.networkprotection.internal.feature.unsafe_wifi.UnsafeWifiDetectionSettingView.State.Disable
import com.duckduckgo.networkprotection.internal.feature.unsafe_wifi.UnsafeWifiDetectionSettingView.State.Enable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class UnsafeWifiDetectionViewModel(
    private val unsafeWifiMonitor: UnsafeWifiMonitor,
) : ViewModel() {

    internal fun reduce(event: Event): Flow<State> {
        return when (event) {
            OnDisableIntent -> onDisableIntent()
            OnEnableIntent -> onEnableIntent()
            Init -> onInit()
        }
    }

    private fun onInit(): Flow<State> = flow {
        if (unsafeWifiMonitor.isEnabled()) {
            emit(Enable)
        } else {
            emit(Disable)
        }
    }

    private fun onEnableIntent(): Flow<State> = flow {
        unsafeWifiMonitor.enable()
        onInit()
    }

    private fun onDisableIntent(): Flow<State> = flow {
        unsafeWifiMonitor.disable()
        onInit()
    }

    @Suppress("UNCHECKED_CAST")
    class Factory @Inject constructor(
        private val unsafeWifiMonitor: UnsafeWifiMonitor,
    ) : ViewModelProvider.NewInstanceFactory() {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return with(modelClass) {
                when {
                    isAssignableFrom(UnsafeWifiDetectionViewModel::class.java) -> UnsafeWifiDetectionViewModel(
                        unsafeWifiMonitor,
                    )
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            } as T
        }
    }
}
