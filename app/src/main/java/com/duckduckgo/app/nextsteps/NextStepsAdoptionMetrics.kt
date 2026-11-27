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

package com.duckduckgo.app.nextsteps

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.SyncState
import com.duckduckgo.sync.api.SyncStateMonitor
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Widget and Sync adoption for every Next Steps cohort, control included, whatever the user set them up from. Setups during
 * the linear onboarding are left out, so the metrics only reflect what users adopt once they reach the browser.
 */
interface NextStepsAdoptionMetrics {
    fun onWidgetAdded()
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<NextStepsAdoptionMetrics>())
@ContributesIntoSet(AppScope::class, binding = binding<MainProcessLifecycleObserver>())
class RealNextStepsAdoptionMetrics @Inject constructor(
    private val sender: NextStepsExperimentMetricSender,
    private val userStageStore: UserStageStore,
    private val syncStateMonitor: SyncStateMonitor,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : NextStepsAdoptionMetrics, MainProcessLifecycleObserver {

    override fun onWidgetAdded() {
        appScope.launch(dispatchers.io()) { sendAfterLinearOnboarding(WIDGET_ADDED) }
    }

    override fun onCreate(owner: LifecycleOwner) {
        appScope.launch(dispatchers.io()) {
            // The first state is whatever sync was at app start, so only a later change from off counts as enabling it.
            var previous: SyncState? = null
            syncStateMonitor.syncState().collect { state ->
                if (previous == SyncState.OFF && state != SyncState.OFF) sendAfterLinearOnboarding(SYNC_ENABLED)
                previous = state
            }
        }
    }

    private suspend fun sendAfterLinearOnboarding(metric: String) {
        if (userStageStore.getUserAppStage() == AppStage.NEW) return
        sender.send(metric)
    }

    private companion object {
        const val WIDGET_ADDED = "widget_added"
        const val SYNC_ENABLED = "sync_enabled"
    }
}
