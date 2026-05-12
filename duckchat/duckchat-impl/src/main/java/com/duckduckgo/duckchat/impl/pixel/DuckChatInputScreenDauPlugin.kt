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

package com.duckduckgo.duckchat.impl.pixel

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_DUCKAI_DAU_TOGGLE_NEVER_ENABLED
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class DuckChatInputScreenDauPlugin @Inject constructor(
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
    private val pixel: Pixel,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : AtbLifecyclePlugin {

    override fun onDuckAiRetentionAtbRefreshed(
        oldAtb: String,
        newAtb: String,
        metadata: Map<String, String?>,
    ) {
        if (oldAtb.atbAsNumber() == newAtb.atbAsNumber()) return
        coroutineScope.launch(dispatcherProvider.io()) {
            if (!duckChatFeatureRepository.isInputScreenEverEnabled()) {
                pixel.fire(DUCK_CHAT_DUCKAI_DAU_TOGGLE_NEVER_ENABLED)
            }
        }
    }

    /**
     * Converts an ATB version string (e.g. "v123-2ma") to a monotonically increasing integer.
     * The variant suffix (e.g. "ma") is intentionally ignored so that strings representing the
     * same week/day but different cohort tags compare as equal.
     */
    internal fun String.atbAsNumber(): Int {
        val startIndex = indexOf('v') + 1
        val endIndex = indexOf('-', startIndex)
        val week = if (endIndex > 0 && startIndex < endIndex) {
            substring(startIndex, endIndex).toIntOrNull() ?: 0
        } else {
            substringAfter('v', "").toIntOrNull() ?: 0
        }
        val day = substringAfterLast('-', "").firstOrNull()?.digitToIntOrNull() ?: 0
        return week * 7 + day - 1
    }
}
