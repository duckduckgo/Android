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

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.store.HideAiGeneratedImages
import com.duckduckgo.duckchat.impl.store.SearchAssistVisibility
import com.duckduckgo.settings.api.SerpSettingsDataProvider
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

interface AiFeaturesStateReporter {
    /**
     * Samples the AI-features settings and fires [DuckChatPixelName.AI_FEATURES_STATE_DAILY] so we can see the
     * distribution across the whole active base. If a stored SERP value can't decode to its native enum, also fires
     * [DuckChatPixelName.SERP_SETTINGS_UNRECOGNIZED_VALUE]. The fired pixel is a daily pixel, so it is deduped per
     * calendar day regardless of how often this is called.
     */
    suspend fun reportDailyState()
}

@ContributesBinding(AppScope::class)
class RealAiFeaturesStateReporter @Inject constructor(
    private val duckChat: DuckChatInternal,
    private val serpSettingsDataProvider: SerpSettingsDataProvider,
    private val pixel: Pixel,
) : AiFeaturesStateReporter {

    override suspend fun reportDailyState() {
        val duckAiOn = duckChat.observeEnableDuckChatUserSetting().firstOrNull() ?: false

        val rawKbe = serpSettingsDataProvider.observeSetting(SearchAssistVisibility.SERP_SETTINGS_KEY).firstOrNull()
        val searchAssist = resolveSearchAssist(rawKbe)

        val rawKbj = serpSettingsDataProvider.observeSetting(HideAiGeneratedImages.SERP_SETTINGS_KEY).firstOrNull()
        val hideImages = resolveHideImages(rawKbj)

        val noAi = !duckAiOn &&
            searchAssist == SearchAssistVisibility.NEVER.serpCode &&
            hideImages == HideAiGeneratedImages.ON

        pixel.fire(
            DuckChatPixelName.AI_FEATURES_STATE_DAILY,
            mapOf(
                DuckChatPixelParameters.DUCK_AI to duckAiOn.toString(),
                DuckChatPixelParameters.SEARCH_ASSIST to searchAssist,
                DuckChatPixelParameters.HIDE_AI_IMAGES to if (hideImages == HideAiGeneratedImages.ON) "on" else "off",
                DuckChatPixelParameters.NO_AI to noAi.toString(),
            ),
            type = Pixel.PixelType.Daily(),
        )
    }

    // Returns the raw kbe value ("0".."3"). Unsynced (null) -> default "2" (Sometimes). A stored-but-unknown
    // value fires the unrecognized-value debug pixel and falls back to the default.
    private fun resolveSearchAssist(rawKbe: String?): String {
        if (rawKbe == null) return DEFAULT_SEARCH_ASSIST_CODE
        if (SearchAssistVisibility.fromSerpCode(rawKbe) == null) {
            fireUnrecognizedValue()
            return DEFAULT_SEARCH_ASSIST_CODE
        }
        return rawKbe
    }

    // null/unknown -> OFF. A stored-but-unknown value fires the unrecognized-value debug pixel.
    private fun resolveHideImages(rawKbj: String?): HideAiGeneratedImages {
        if (rawKbj != null && HideAiGeneratedImages.entries.none { it.serpCode == rawKbj }) {
            fireUnrecognizedValue()
        }
        return HideAiGeneratedImages.fromSerpCode(rawKbj)
    }

    private fun fireUnrecognizedValue() {
        pixel.fire(DuckChatPixelName.SERP_SETTINGS_UNRECOGNIZED_VALUE, type = Pixel.PixelType.Daily())
    }

    private companion object {
        private const val DEFAULT_SEARCH_ASSIST_CODE = "2" // Sometimes — matches DuckChatSettingsViewModel default.
    }
}
