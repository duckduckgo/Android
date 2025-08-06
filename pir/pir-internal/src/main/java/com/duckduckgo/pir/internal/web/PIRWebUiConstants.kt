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

package com.duckduckgo.pir.internal.web

object PIRWebUiConstants {
    internal const val WEB_UI_URL = "https://duckduckgo.com/dbp"

    // internal const val SCRIPT_CONTEXT_NAME = "dbpui"
    internal const val SCRIPT_FEATURE_NAME = "dbpui" // "dbpuiCommunication"
    internal const val SCRIPT_CONTEXT_NAME = "contentScopeScripts"
    internal const val MESSAGE_CALLBACK = "messageCallback"
    internal const val SECRET = "duckduckgo-android-messaging-secret" // "messageSecret"
    // internal const val SCRIPT_FEATURE_NAME = "brokerProtection"
}
