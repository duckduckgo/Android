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

package com.duckduckgo.adblocking.impl.pixels

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_STATE_DAILY
import com.duckduckgo.adblocking.impl.domain.AdBlockingState
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reports the client-side ad blocking state once per day, on app foreground.
 * The [Pixel.PixelType.Daily] dedup guarantees at most one report per UTC day.
 */
@ContributesMultibinding(scope = AppScope::class, boundType = MainProcessLifecycleObserver::class)
@SingleInstanceIn(AppScope::class)
class AdBlockingStateReporter @Inject constructor(
    private val statusChecker: AdBlockingStatusChecker,
    private val pixel: Pixel,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : MainProcessLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        appScope.launch {
            val (isActive, userState) = combine(
                statusChecker.observeCanInject(),
                statusChecker.observeState(),
            ) { isActive, userState -> isActive to userState }.firstOrNull() ?: return@launch
            pixel.fire(
                AD_BLOCKING_STATE_DAILY,
                parameters = mapOf(
                    PARAM_IS_ENABLED to isActive.toString(),
                    PARAM_ANALYTICS_ENABLED to (userState is AdBlockingState.Enabled.UserEnabled).toString(),
                ),
                type = Pixel.PixelType.Daily(),
            )
        }
    }

    private companion object {
        private const val PARAM_IS_ENABLED = "is_enabled"
        private const val PARAM_ANALYTICS_ENABLED = "analytics_enabled"
    }
}
