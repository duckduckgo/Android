/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.duckplayer

import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.js.messaging.api.JsCallbackData
import javax.inject.Inject
import org.json.JSONObject

class DuckPlayerJSHelper @Inject constructor(
    private val duckPlayer: DuckPlayer,
) {
    suspend fun getUserValues(featureName: String, method: String, id: String): JsCallbackData {
        val userValues = duckPlayer.getUserValues()

        return JsCallbackData(
            JSONObject(
                """
                {
                    "overlayInteracted": ${userValues.overlayInteracted},
                    "privatePlayerMode": {
                      "${userValues.privatePlayerMode}": {}
                    }
                  }
                  """,
            ),
            featureName,
            method,
            id,
        )
    }

    fun setUserValues(data: JSONObject) {
        val overlayInteracted = data.getBoolean("overlayInteracted")
        val privatePlayerModeObject = data.getJSONObject("privatePlayerMode")
        duckPlayer.setUserValues(overlayInteracted, privatePlayerModeObject.keys().next())
    }

    fun sendDuckPlayerPixel(data: JSONObject) {
        val pixelName = data.getString("pixelName")
        val paramsMap = data.getJSONObject("params").keys().asSequence().associateWith {
            data.getJSONObject("params").getString(it)
        }
        duckPlayer.sendDuckPlayerPixel(pixelName, paramsMap)
    }
}
