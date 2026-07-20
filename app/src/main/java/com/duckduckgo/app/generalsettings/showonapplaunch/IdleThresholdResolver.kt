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

package com.duckduckgo.app.generalsettings.showonapplaunch

import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import org.json.JSONObject
import javax.inject.Inject

/**
 * Single source for the effective after-idle timeout. Resolves, in order: the user's chosen
 * value, else the remote-config default, else the hardcoded default.
 */
interface IdleThresholdResolver {
    fun effectiveThresholdSeconds(userSelectedSeconds: Long?): Long
}

@ContributesBinding(AppScope::class)
class RealIdleThresholdResolver @Inject constructor(
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
) : IdleThresholdResolver {

    override fun effectiveThresholdSeconds(userSelectedSeconds: Long?): Long =
        userSelectedSeconds
            ?: parseRemoteDefaultSeconds(androidBrowserConfigFeature.showNTPAfterIdleReturn().getSettings())
            ?: FirstScreenHandlerImpl.DEFAULT_IDLE_THRESHOLD_SECONDS

    private fun parseRemoteDefaultSeconds(settingsJson: String?): Long? {
        if (settingsJson == null) return null
        return try {
            val json = JSONObject(settingsJson)
            if (json.has("defaultIdleThresholdSeconds")) json.getLong("defaultIdleThresholdSeconds") else null
        } catch (e: Exception) {
            null
        }
    }
}
