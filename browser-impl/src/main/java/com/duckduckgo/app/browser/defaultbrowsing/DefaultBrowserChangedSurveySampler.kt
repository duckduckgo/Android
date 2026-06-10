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

package com.duckduckgo.app.browser.defaultbrowsing

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import kotlin.random.Random

interface DefaultBrowserChangedSurveySampler {
    /**
     * Returns true if the current evaluation falls into the configured per-evaluation sample
     * (i.e. the survey may be shown). The sampling rate is read from the survey toggle's remote
     * `settings` payload as `{ "samplingRate": <0-100> }`. Defaults to 100% if the setting is
     * missing or malformed.
     */
    suspend fun isInSample(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDefaultBrowserChangedSurveySampler @Inject constructor(
    defaultBrowserChangedSurveyFeature: DefaultBrowserChangedSurveyFeature,
    private val dispatchers: DispatcherProvider,
    private val random: Random = Random.Default,
) : DefaultBrowserChangedSurveySampler {

    private var samplingRate: Int

    init {
        val settingsJson = defaultBrowserChangedSurveyFeature.self().getSettings()
        samplingRate = if (settingsJson == null) {
            DEFAULT_SAMPLING_RATE
        } else {
            try {
                val json = JSONObject(settingsJson)
                if (json.has(KEY_SAMPLING_RATE)) {
                    json.getInt(KEY_SAMPLING_RATE).coerceIn(0, MAX_SAMPLE_RANGE)
                } else {
                    DEFAULT_SAMPLING_RATE
                }
            } catch (_: Exception) {
                DEFAULT_SAMPLING_RATE
            }
        }
    }

    override suspend fun isInSample(): Boolean = withContext(dispatchers.io()) {
        random.nextInt(MAX_SAMPLE_RANGE) < samplingRate
    }

    companion object {
        private const val DEFAULT_SAMPLING_RATE = 100
        private const val MAX_SAMPLE_RANGE = 100
        private const val KEY_SAMPLING_RATE = "samplingRate"
    }
}
