/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.dashboard

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.store.PirRepository
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ActivityScope::class)
class PirDashboardWebViewViewModel @Inject constructor(
    private val pirPixelSender: PirPixelSender,
    private val appBuildConfig: AppBuildConfig,
    private val pirRepository: PirRepository,
) : ViewModel(), DefaultLifecycleObserver {

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    fun handleJsMessage(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
    ) {
        // TODO Handle any JS messages that requires UI updates or other user actions
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        pirPixelSender.reportDashboardOpened()

        if (appBuildConfig.isInternalBuild()) {
            viewModelScope.launch {
                command.send(
                    Command.ShowManualConfigWarning(pirRepository.hasBrokerConfigBeenManuallyUpdated()),
                )
            }
        }
    }

    sealed class Command {
        data class SendJsEvent(val event: SubscriptionEventData) : Command()
        data class SendResponseToJs(val data: JsCallbackData) : Command()
        data class ShowManualConfigWarning(val show: Boolean) : Command()
    }
}
