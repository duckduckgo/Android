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

package com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.usage

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

interface InputScreenSessionUsageMetric {
    fun onSearchSubmitted()
    fun onPromptSubmitted()
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = InputScreenSessionUsageMetric::class,
)
@SingleInstanceIn(scope = AppScope::class)
class InputScreenSessionUsageMetricImpl @Inject constructor(
    private val pixel: Pixel,
    private val duckAiFeatureState: DuckAiFeatureState,
) : MainProcessLifecycleObserver, InputScreenSessionUsageMetric {

    private companion object {
        private const val PIXEL_PARAM_SEARCH_COUNT = "searches_in_session"
        private const val PIXEL_PARAM_PROMPT_COUNT = "prompts_in_session"
    }

    private val searchCounter: AtomicInteger = AtomicInteger()
    private val promptCounter: AtomicInteger = AtomicInteger()

    override fun onStop(owner: LifecycleOwner) {
        if (!duckAiFeatureState.showInputScreen.value) {
            return
        }
        val searchCount = searchCounter.getAndSet(0)
        val promptCount = promptCounter.getAndSet(0)
        val params = mapOf(
            PIXEL_PARAM_SEARCH_COUNT to searchCount.toString(),
            PIXEL_PARAM_PROMPT_COUNT to promptCount.toString(),
        )
        pixel.enqueueFire(
            pixel = DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_SUMMARY,
            parameters = params,
        )
    }

    override fun onSearchSubmitted() {
        searchCounter.incrementAndGet()
    }

    override fun onPromptSubmitted() {
        promptCounter.incrementAndGet()
    }
}
