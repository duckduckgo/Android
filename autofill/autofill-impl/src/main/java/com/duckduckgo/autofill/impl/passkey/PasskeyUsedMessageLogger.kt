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
 * Parses the `webCompat` / `passkeyUsed` notification sent by content-scope-scripts when a
 * WebAuthn ceremony completes, and logs the outcome.
 *
 * Payload contract (see `injected/src/messages/web-compat/passkeyUsed.notify.json` in C-S-S):
 * ```
 * { "type": "get" | "create", "success": boolean, "error"?: string }
 * ```
 * `error` is a sanitized DOMException name and is only present when `success` is false.
 *
 * Pixel firing is intentionally not implemented here yet.
 */
class PasskeyUsedMessageLogger @Inject constructor() {

    fun log(params: JSONObject) {
        // TEMP diagnostic: android.util.Log writes regardless of the debuggable flag or the
        // logcat{} logger being installed. Remove once the message flow is confirmed on device.
        Log.i(DIAG_TAG, "log() reached with params=$params")

        val type = params.optString(PARAM_TYPE)
        if (type != TYPE_GET && type != TYPE_CREATE) {
            logcat(WARN) { "Passkey: ignoring message with unsupported type '$type'" }
            return
        }

        val success = params.opt(PARAM_SUCCESS) as? Boolean
        if (success == null) {
            logcat(WARN) { "Passkey: ignoring $type message without a boolean '$PARAM_SUCCESS'" }
            return
        }

        if (success) {
            logcat(INFO) { "Passkey: $type succeeded" }
        } else {
            val error = params.optString(PARAM_ERROR).ifEmpty { "unspecified" }
            logcat(INFO) { "Passkey: $type failed with $error" }
        }
    }

    private companion object {
        const val DIAG_TAG = "PasskeyUsedDbg"
        const val PARAM_TYPE = "type"
        const val PARAM_SUCCESS = "success"
        const val PARAM_ERROR = "error"
        const val TYPE_GET = "get"
        const val TYPE_CREATE = "create"
    }
}
