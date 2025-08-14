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

package com.duckduckgo.pir.impl.dashboard.messaging

object PirDashboardWebConstants {
    const val WEB_UI_URL = "https://duckduckgo.com/dbp"
    const val CUSTOM_UA = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko)" +
        " Version/4.0 Chrome/124.0.0.0 Mobile DuckDuckGo/5 Safari/537.36"

    internal const val SCRIPT_API_VERSION = 11
    internal const val SCRIPT_CONTEXT_NAME = "dbpui"
    internal const val SCRIPT_FEATURE_NAME = "dbpuiCommunication"
    internal const val MESSAGE_CALLBACK = "messageCallback"
    internal const val SECRET = "duckduckgo-android-messaging-secret"
    internal const val ALLOWED_DOMAIN = "duckduckgo.com"

    internal const val PARAM_SUCCESS = "success"
    internal const val PARAM_VERSION = "version"
    internal const val PARAM_FIRST_NAME = "first"
    internal const val PARAM_MIDDLE_NAME = "middle"
    internal const val PARAM_LAST_NAME = "last"
    internal const val PARAM_CITY = "city"
    internal const val PARAM_STATE = "state"
}
