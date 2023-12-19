/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.browser.orientation

import android.content.pm.ActivityInfo
import androidx.fragment.app.FragmentActivity
import com.duckduckgo.app.global.view.requestJsOrientationChange
import com.duckduckgo.js.messaging.api.JsCallbackData
import org.json.JSONObject

class JsOrientationHandler {

    /**
     * Updates the activity's orientation based on provided JS data
     *
     * @return response data
     */
    fun updateOrientation(data: JsCallbackData, activity: FragmentActivity?): JsCallbackData {
        val response = if (activity == null) {
            NO_ACTIVITY_ERROR
        } else {
            val requestedOrientation = data.params.getString("orientation")
            val matchedOrientation = JavaScriptScreenOrientation.values().find { it.jsValue == requestedOrientation }

            if (matchedOrientation == null) {
                String.format(TYPE_ERROR, requestedOrientation)
            } else if (!activity.requestJsOrientationChange(matchedOrientation)) {
                NOT_FULL_SCREEN_ERROR
            } else {
                EMPTY
            }
        }

        return JsCallbackData(
            JSONObject(response),
            data.featureName,
            data.method,
            data.id,
        )
    }

    companion object {
        const val EMPTY = """{}"""
        const val NOT_FULL_SCREEN_ERROR = """{"failure":{"name":"InvalidStateError","message":"The page needs to be fullscreen in order to call screen.orientation.lock()"}}"""
        const val TYPE_ERROR = """{"failure":{"name":"TypeError","message":"Failed to execute 'lock' on 'ScreenOrientation': The provided value '%s' is not a valid enum value of type OrientationLockType."}}"""
        const val NO_ACTIVITY_ERROR = """{"failure":{"name":"InvalidStateError","message":"The page is not tied to an activity"}}"""
    }
}

enum class JavaScriptScreenOrientation(val jsValue: String, val nativeValue: Int) {
    ANY("any", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    NATURAL("natural", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    LANDSCAPE("landscape", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    PORTRAIT("portrait", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    PORTRAIT_PRIMARY("portrait-primary", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    PORTRAIT_SECONDARY("portrait-secondary", ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
    LANDSCAPE_PRIMARY("landscape-primary", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    LANDSCAPE_SECONDARY("landscape-secondary", ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE),
}
