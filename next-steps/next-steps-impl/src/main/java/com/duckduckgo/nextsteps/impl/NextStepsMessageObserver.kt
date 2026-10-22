/*
 * Copyright (c) 2026 DuckDuckGo
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
package com.duckduckgo.nextsteps.impl

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesIntoSet(AppScope::class, binding = binding<MainProcessLifecycleObserver>())
class NextStepsMessageObserver @Inject constructor(
    private val remoteMessageModel: RemoteMessageModel,
    private val telemetry: NextStepsTelemetry,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : MainProcessLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {
        appScope.launch(dispatchers.io()) {
            remoteMessageModel.observeActiveMessages()
                .map { it?.nextStepsMessageId() }
                .distinctUntilChanged()
                .collect {
                    // Decide on the live slot, not the emission: a delayed emission must not close a message that
                    // has since been shown again.
                    telemetry.onActiveMessageChanged(remoteMessageModel.getActiveMessage()?.nextStepsMessageId())
                }
        }
    }

    private fun RemoteMessage.nextStepsMessageId(): String? = id.takeIf { isNextStepsForNewTabPage() }
}
