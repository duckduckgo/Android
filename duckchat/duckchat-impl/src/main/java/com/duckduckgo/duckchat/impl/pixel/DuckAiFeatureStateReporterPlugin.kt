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

package com.duckduckgo.duckchat.impl.pixel

import com.duckduckgo.app.statistics.api.BrowserFeatureStateReporterPlugin
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.toBinaryString
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Reports which Duck.ai capabilities are active for the user on the daily feature state pixel.
 */
@ContributesMultibinding(scope = AppScope::class, boundType = BrowserFeatureStateReporterPlugin::class)
@SingleInstanceIn(AppScope::class)
class DuckAiFeatureStateReporterPlugin @Inject constructor(
    private val duckAiFeatureState: DuckAiFeatureState,
    private val duckChatFeature: DuckChatFeature,
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
    private val dispatcherProvider: DispatcherProvider,
) : BrowserFeatureStateReporterPlugin {

    override fun featureStateParams(): Map<String, String> {
        val inputMode = runBlocking(dispatcherProvider.io()) {
            resolveInputModeCapability()
        }
        return mapOf(
            DUCK_AI_INPUT_MODE to inputMode.pixelValue(),
            DUCK_AI_NATIVE_INPUT to duckAiFeatureState.nativeInputFieldEnabled.value.toBinaryString(),
            DUCK_AI_CONTEXTUAL to duckAiFeatureState.showContextualMode.value.toBinaryString(),
        )
    }

    private suspend fun resolveInputModeCapability(): NativeInputState.InputMode {
        return if (
            duckChatFeature.self().isEnabled() &&
            duckChatFeatureRepository.isDuckChatUserEnabled() &&
            duckChatFeatureRepository.isInputScreenUserSettingEnabled()
        ) {
            NativeInputState.InputMode.SEARCH_AND_DUCK_AI
        } else {
            NativeInputState.InputMode.SEARCH_ONLY
        }
    }

    private fun NativeInputState.InputMode.pixelValue(): String = when (this) {
        NativeInputState.InputMode.SEARCH_AND_DUCK_AI -> "search_and_duck_ai"
        NativeInputState.InputMode.SEARCH_ONLY -> "search_only"
    }

    companion object {
        const val DUCK_AI_INPUT_MODE = "duck_ai_input_mode"
        const val DUCK_AI_NATIVE_INPUT = "duck_ai_native_input"
        const val DUCK_AI_CONTEXTUAL = "duck_ai_contextual"
    }
}
