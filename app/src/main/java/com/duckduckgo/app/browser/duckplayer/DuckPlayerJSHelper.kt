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

import androidx.core.net.toUri
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.commands.Command
import com.duckduckgo.app.browser.commands.Command.OpenDuckPlayerOverlayInfo
import com.duckduckgo.app.browser.commands.Command.OpenDuckPlayerPageInfo
import com.duckduckgo.app.browser.commands.Command.OpenDuckPlayerSettings
import com.duckduckgo.app.browser.commands.Command.SendResponseToDuckPlayer
import com.duckduckgo.app.browser.commands.Command.SendResponseToJs
import com.duckduckgo.app.browser.commands.Command.SendSubscriptions
import com.duckduckgo.app.browser.commands.NavigationCommand.Navigate
import com.duckduckgo.app.pixels.AppPixelName.DUCK_PLAYER_SETTING_ALWAYS_DUCK_PLAYER
import com.duckduckgo.app.pixels.AppPixelName.DUCK_PLAYER_SETTING_ALWAYS_OVERLAY_YOUTUBE
import com.duckduckgo.app.pixels.AppPixelName.DUCK_PLAYER_SETTING_ALWAYS_SERP
import com.duckduckgo.app.pixels.AppPixelName.DUCK_PLAYER_SETTING_NEVER_OVERLAY_YOUTUBE
import com.duckduckgo.app.pixels.AppPixelName.DUCK_PLAYER_SETTING_NEVER_SERP
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.ENABLED
import com.duckduckgo.duckplayer.api.DuckPlayer.UserPreferences
import com.duckduckgo.duckplayer.api.ORIGIN_QUERY_PARAM
import com.duckduckgo.duckplayer.api.ORIGIN_QUERY_PARAM_AUTO
import com.duckduckgo.duckplayer.api.ORIGIN_QUERY_PARAM_OVERLAY
import com.duckduckgo.duckplayer.api.PrivatePlayerMode
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.Enabled
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.SubscriptionEventData
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
    private val pixel: Pixel,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
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

    fun userPreferencesUpdated(userPreferences: UserPreferences): SendSubscriptions {
        return JSONObject(
            """
                {
                    $OVERLAY_INTERACTED: ${userPreferences.overlayInteracted},
                    $PRIVATE_PLAYER_MODE: {
                      "${userPreferences.privatePlayerMode.value}": {}
                    }
                  }
                  """,
        ).let { json ->
            SendSubscriptions(
                cssData = SubscriptionEventData(DUCK_PLAYER_FEATURE_NAME, "onUserValuesChanged", json),
                duckPlayerData = SubscriptionEventData(DUCK_PLAYER_PAGE_FEATURE_NAME, "onUserValuesChanged", json),
            )
        }
    }

    private suspend fun getInitialSetup(featureName: String, method: String, id: String): JsCallbackData {
        val userValues = duckPlayer.getUserPreferences()
        val privatePlayerMode = if (duckPlayer.getDuckPlayerState() == ENABLED) userValues.privatePlayerMode else PrivatePlayerMode.Disabled

        val jsonObject = JSONObject(
            """
                {
                    "settings": {
                        "pip": {
                            "state": "disabled"
                        }
                    },
                    "userValues": {
                        $OVERLAY_INTERACTED: ${userValues.overlayInteracted},
                        $PRIVATE_PLAYER_MODE: {
                          "${privatePlayerMode.value}": {}
                        }
                    },
                    ui: {
                        "allowFirstVideo": ${duckPlayer.shouldHideDuckPlayerOverlay()}
                    }
               }
               """,
        )
        duckPlayer.duckPlayerOverlayHidden()

        when (featureName) {
            DUCK_PLAYER_PAGE_FEATURE_NAME -> {
                jsonObject.put("platform", JSONObject("""{ name: "android" }"""))
                jsonObject.put("locale", java.util.Locale.getDefault().language)
                jsonObject.put("env", if (appBuildConfig.isDebug) "development" else "production")
            }
            DUCK_PLAYER_FEATURE_NAME -> {
                jsonObject.put("platform", JSONObject("""{ name: "android" }"""))
                jsonObject.put("locale", java.util.Locale.getDefault().language)
            }
        }

        return JsCallbackData(
            jsonObject,
            featureName,
            method,
            id,
        )
    }

    private suspend fun setUserPreferences(data: JSONObject) {
        val overlayInteracted = data.getBoolean(OVERLAY_INTERACTED)
        val privatePlayerModeObject = data.getJSONObject(PRIVATE_PLAYER_MODE)
        duckPlayer.setUserPreferences(overlayInteracted, privatePlayerModeObject.keys().next())
    }

    private suspend fun sendDuckPlayerPixel(data: JSONObject) {
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
        url: String?,
    ): Command? {
        when (method) {
            "getUserValues" -> if (id != null) {
                return SendResponseToJs(getUserPreferences(featureName, method, id))
            }

            "setUserValues" -> if (id != null && data != null) {
                setUserPreferences(data)
                return when (featureName) {
                    DUCK_PLAYER_FEATURE_NAME -> {
                        SendResponseToJs(getUserPreferences(featureName, method, id)).also {
                            if (data.getJSONObject(PRIVATE_PLAYER_MODE).keys().next() == "enabled") {
                                if (url != null && duckDuckGoUrlDetector.isDuckDuckGoUrl(url)) {
                                    pixel.fire(DUCK_PLAYER_SETTING_ALWAYS_SERP)
                                } else {
                                    pixel.fire(DUCK_PLAYER_SETTING_ALWAYS_OVERLAY_YOUTUBE)
                                }
                            } else if (data.getJSONObject(PRIVATE_PLAYER_MODE).keys().next() == "disabled") {
                                if (url != null && duckDuckGoUrlDetector.isDuckDuckGoUrl(url)) {
                                    pixel.fire(DUCK_PLAYER_SETTING_NEVER_SERP)
                                } else {
                                    pixel.fire(DUCK_PLAYER_SETTING_NEVER_OVERLAY_YOUTUBE)
                                }
                            }
                        }
                    }
                    DUCK_PLAYER_PAGE_FEATURE_NAME -> {
                        SendResponseToDuckPlayer(getUserPreferences(featureName, method, id)).also {
                            if (data.getJSONObject(PRIVATE_PLAYER_MODE).keys().next() == "enabled") {
                                pixel.fire(DUCK_PLAYER_SETTING_ALWAYS_DUCK_PLAYER)
                            }
                        }
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
                    if (duckPlayer.getUserPreferences().privatePlayerMode == Enabled) {
                        Navigate(it.toUri().buildUpon().appendQueryParameter(ORIGIN_QUERY_PARAM, ORIGIN_QUERY_PARAM_AUTO).build().toString(), mapOf())
                    } else {
                        Navigate(
                            it.toUri().buildUpon().appendQueryParameter(ORIGIN_QUERY_PARAM, ORIGIN_QUERY_PARAM_OVERLAY).build().toString(),
                            mapOf(),
                        )
                    }
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
            "openInfo" -> {
                return when (featureName) {
                    DUCK_PLAYER_FEATURE_NAME -> OpenDuckPlayerOverlayInfo
                    DUCK_PLAYER_PAGE_FEATURE_NAME -> OpenDuckPlayerPageInfo
                    else -> null
                }
            }
            else -> {
                return null
            }
        }
        return null
    }
}
