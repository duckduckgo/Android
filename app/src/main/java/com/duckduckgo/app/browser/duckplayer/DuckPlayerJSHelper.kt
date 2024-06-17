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

import com.duckduckgo.app.browser.commands.Command
import com.duckduckgo.app.browser.commands.Command.SendResponseToDuckPlayer
import com.duckduckgo.app.browser.commands.Command.SendResponseToJs
import com.duckduckgo.app.browser.commands.NavigationCommand.Navigate
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.js.messaging.api.JsCallbackData
import javax.inject.Inject
import org.json.JSONObject

class DuckPlayerJSHelper @Inject constructor(
    private val duckPlayer: DuckPlayer,
) {
    private suspend fun getUserPreferences(featureName: String, method: String, id: String): JsCallbackData {
        val userValues = duckPlayer.getUserPreferences()

        return JsCallbackData(
            JSONObject(
                """
                {
                    "overlayInteracted": ${userValues.overlayInteracted},
                    "privatePlayerMode": {
                      "${userValues.privatePlayerMode.value}": {}
                    }
                  }
                  """,
            ),
            featureName,
            method,
            id,
        )
    }

    private suspend fun getInitialSetup(featureName: String, method: String, id: String): JsCallbackData {
        val userValues = duckPlayer.getUserPreferences()

        return JsCallbackData(
            JSONObject(
                """
                {
                    "settings": {
                        "pip": {
                            "state": "enabled"
                        }
                    },
                    "userValues": {
                        "overlayInteracted": ${userValues.overlayInteracted},
                        "privatePlayerMode": {
                          "${userValues.privatePlayerMode.value}": {}
                        }
                  }
               }
               """,
            ),
            featureName,
            method,
            id,
        )
    }

    private fun setUserPreferences(data: JSONObject) {
        val overlayInteracted = data.getBoolean("overlayInteracted")
        val privatePlayerModeObject = data.getJSONObject("privatePlayerMode")
        duckPlayer.setUserPreferences(overlayInteracted, privatePlayerModeObject.keys().next())
    }

    private fun sendDuckPlayerPixel(data: JSONObject) {
        val pixelName = data.getString("pixelName")
        val paramsMap = data.getJSONObject("params").keys().asSequence().associateWith {
            data.getJSONObject("params").getString(it)
        }
        duckPlayer.sendDuckPlayerPixel(pixelName, paramsMap)
    }

    suspend fun processJsCallbackMessage(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
    ): Command? {
        when (method) {
            "getUserValues" -> if (id != null) {
                return SendResponseToJs(getUserPreferences(featureName, method, id))
            }

            "setUserValues" -> if (id != null && data != null) {
                setUserPreferences(data)
                return SendResponseToJs(getUserPreferences(featureName, method, id))
            }

            "sendDuckPlayerPixel" -> if (data != null) {
                sendDuckPlayerPixel(data)
                return null
            }
            "openDuckPlayer" -> {
                return data?.getString("href")?.let {
                    Navigate(it, mapOf())
                }
            }
            "initialSetup" -> {
                return SendResponseToDuckPlayer(getInitialSetup(featureName, method, id ?: ""))
            }
            else -> {
                return null
            }
        }
        return null
    }
}
