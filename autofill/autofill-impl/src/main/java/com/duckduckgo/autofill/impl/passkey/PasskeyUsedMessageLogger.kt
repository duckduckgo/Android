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

package com.duckduckgo.autofill.impl.passkey

import android.util.Log
import logcat.LogPriority.INFO
import logcat.LogPriority.WARN
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

/**
 * Logs `webCompat` / `passkeyUsed` and `webCompat` / `passkeyFailed` notifications
 * from content-scope-scripts when a WebAuthn ceremony completes.
 *
 * Payload contracts:
 * - `passkeyUsed`: `{ "type": "get" | "create" }`
 * - `passkeyFailed`: `{ "type": "get" | "create", "error": string }`
 *
 * Pixel firing is intentionally not implemented here yet.
 */
class PasskeyUsedMessageLogger @Inject constructor() {

    fun logUsed(params: JSONObject) {
        Log.i(DIAG_TAG, "logUsed() reached with params=$params")
        val type = ceremonyType(params) ?: return
        logcat(INFO) { "Passkey: $type succeeded" }
    }

    fun logFailed(params: JSONObject) {
        Log.i(DIAG_TAG, "logFailed() reached with params=$params")
        val type = ceremonyType(params) ?: return
        val error = params.optString(PARAM_ERROR).ifEmpty { "unspecified" }
        logcat(INFO) { "Passkey: $type failed with $error" }
    }

    private fun ceremonyType(params: JSONObject): String? {
        val type = params.optString(PARAM_TYPE)
        if (type != TYPE_GET && type != TYPE_CREATE) {
            logcat(WARN) { "Passkey: ignoring message with unsupported type '$type'" }
            return null
        }
        return type
    }

    private companion object {
        const val DIAG_TAG = "PasskeyUsedDbg"
        const val PARAM_TYPE = "type"
        const val PARAM_ERROR = "error"
        const val TYPE_GET = "get"
        const val TYPE_CREATE = "create"
    }
}
