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
import com.duckduckgo.app.browser.commands.Command.OpenDuckPlayerSettings
import com.duckduckgo.app.browser.commands.Command.SendResponseToDuckPlayer
import com.duckduckgo.app.browser.commands.Command.SendResponseToJs
import com.duckduckgo.app.browser.commands.NavigationCommand.Navigate
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Disabled
import com.duckduckgo.js.messaging.api.JsCallbackData
import javax.inject.Inject
import org.json.JSONObject
import timber.log.Timber

const val DUCK_PLAYER_PAGE_FEATURE_NAME = "duckPlayerPage"
const val DUCK_PLAYER_FEATURE_NAME = "duckPlayer"
private const val OVERLAY_INTERACTED = "overlayInteracted"
private const val PRIVATE_PLAYER_MODE = "privatePlayerMode"

class DuckPlayerJSHelper @Inject constructor(
    private val duckPlayer: DuckPlayer,
    private val appBuildConfig: AppBuildConfig,
) {
    private suspend fun getUserPreferences(featureName: String, method: String, id: String): JsCallbackData {
        val userValues = duckPlayer.getUserPreferences()

        return JsCallbackData(
            JSONObject(
                """
                {
                    $OVERLAY_INTERACTED: ${userValues.overlayInteracted},
                    $PRIVATE_PLAYER_MODE: {
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
        val userValues = duckPlayer.getUserPreferences().let {
            if (it.privatePlayerMode == AlwaysAsk && duckPlayer.shouldHideDuckPlayerOverlay()) {
                duckPlayer.duckPlayerOverlayHidden()
                it.copy(overlayInteracted = false, privatePlayerMode = Disabled)
            } else {
                it
            }
        }

        val jsonObject = JSONObject(
            """
                {
                    "settings": {
                        "pip": {
                            "state": "enabled"
                        }
                    },
                    "userValues": {
                        $OVERLAY_INTERACTED: ${userValues.overlayInteracted},
                        $PRIVATE_PLAYER_MODE: {
                          "${userValues.privatePlayerMode.value}": {}
                        }
                  }
               }
               """,
        )

        if (featureName == DUCK_PLAYER_PAGE_FEATURE_NAME) {
            jsonObject.put("platform", JSONObject("""{ name: "android" }"""))
            jsonObject.put("locale", java.util.Locale.getDefault().language)
            jsonObject.put("env", if (appBuildConfig.isDebug) "development" else "production")
        }

        return JsCallbackData(
            jsonObject,
            featureName,
            method,
            id,
        )
    }

    private fun setUserPreferences(data: JSONObject) {
        val overlayInteracted = data.getBoolean(OVERLAY_INTERACTED)
        val privatePlayerModeObject = data.getJSONObject(PRIVATE_PLAYER_MODE)
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
                return when (featureName) {
                    DUCK_PLAYER_FEATURE_NAME -> {
                        SendResponseToJs(getUserPreferences(featureName, method, id))
                    }
                    DUCK_PLAYER_PAGE_FEATURE_NAME -> {
                        SendResponseToDuckPlayer(getUserPreferences(featureName, method, id))
                    } else -> {
                        null
                    }
                }
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
                return when (featureName) {
                    DUCK_PLAYER_FEATURE_NAME -> {
                        SendResponseToJs(getInitialSetup(featureName, method, id ?: ""))
                    }
                    DUCK_PLAYER_PAGE_FEATURE_NAME -> {
                        SendResponseToDuckPlayer(getInitialSetup(featureName, method, id ?: ""))
                    }
                    else -> {
                        null
                    }
                }
            }
            "reportPageException", "reportInitException" -> {
                Timber.tag(method).d(data.toString())
            }
            "openSettings" -> {
                return OpenDuckPlayerSettings
            }
            else -> {
                return null
            }
        }
        return null
    }
}
