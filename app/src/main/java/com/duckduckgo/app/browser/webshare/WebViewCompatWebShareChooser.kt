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

package com.duckduckgo.app.browser.webshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.duckduckgo.js.messaging.api.JsCallbackData
import org.json.JSONObject

class WebViewCompatWebShareChooser : ActivityResultContract<JsCallbackData, JSONObject>() {

    lateinit var data: JsCallbackData
    override fun createIntent(
        context: Context,
        input: JsCallbackData,
    ): Intent {
        data = input
        val url = runCatching { input.params.getString("url") }.getOrNull().orEmpty()
        val text = runCatching { input.params.getString("text") }.getOrNull().orEmpty()
        val title = runCatching { input.params.getString("title") }.getOrNull().orEmpty()

        val finalText = url.ifEmpty { text }

        val getContentIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, finalText)
            if (title.isNotEmpty()) {
                putExtra(Intent.EXTRA_TITLE, title)
            }
        }

        return Intent.createChooser(getContentIntent, title)
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): JSONObject {
        val result = if (this::data.isInitialized) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    JSONObject(EMPTY)
                }
                Activity.RESULT_CANCELED -> {
                    JSONObject(ABORT_ERROR)
                }
                else -> {
                    JSONObject(DATA_ERROR)
                }
            }
        } else {
            JSONObject(DATA_ERROR)
        }
        return result
    }

    companion object {
        const val EMPTY = """{}"""
        const val ABORT_ERROR = """{"failure":{"name":"AbortError","message":"Share canceled"}}"""
        const val DATA_ERROR = """{"failure":{"name":"DataError","message":"Data not found"}}"""
    }
}
