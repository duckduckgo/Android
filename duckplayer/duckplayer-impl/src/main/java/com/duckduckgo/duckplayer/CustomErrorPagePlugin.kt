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

package com.duckduckgo.duckplayer

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckplayer.api.DuckPlayerPageSettingsPlugin
import com.duckduckgo.duckplayer.impl.DuckPlayerFeature
import com.squareup.anvil.annotations.ContributesMultibinding
import org.json.JSONObject
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class CustomErrorPagePlugin @Inject constructor(
    private val duckPlayerFeature: DuckPlayerFeature,
) : DuckPlayerPageSettingsPlugin {
    override fun getSettings(): JSONObject {
        val customErrorObject = JSONObject()
        customErrorObject.put("state", if (duckPlayerFeature.customError().isEnabled()) "enabled" else "disabled")
        duckPlayerFeature.customError().getSettings()?.let { settings ->
            customErrorObject.put("settings", JSONObject(settings))
        }

        return customErrorObject
    }

    override fun getName(): String = "customError"
}
