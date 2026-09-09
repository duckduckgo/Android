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

package com.duckduckgo.duckchat.impl.ui.nativeinput.textselection

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.R
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

interface TextSelectionPayloadBuilder {
    fun toJson(selections: List<TextSelection>): JSONArray?

    companion object {
        const val MAX_CONTENT_LENGTH = 9500
    }
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealTextSelectionPayloadBuilder @Inject constructor(
    private val context: Context,
) : TextSelectionPayloadBuilder {

    override fun toJson(selections: List<TextSelection>): JSONArray? {
        val title = context.getString(R.string.duckAiTextSelectionAttachmentTitle)
        if (selections.isEmpty()) return null
        return JSONArray().apply {
            selections.forEach { selection -> put(toJson(selection, title)) }
        }
    }

    private fun toJson(
        selection: TextSelection,
        title: String,
    ): JSONObject {
        val truncated = selection.text.length > TextSelectionPayloadBuilder.MAX_CONTENT_LENGTH
        return JSONObject().apply {
            put("id", selection.id)
            put("title", title)
            put("favicon", JSONArray())
            put("url", selection.url)
            put("content", if (truncated) selection.text.take(TextSelectionPayloadBuilder.MAX_CONTENT_LENGTH) else selection.text)
            put("truncated", truncated)
            put("fullContentLength", selection.text.length)
            put("wordCount", selection.text.split(WHITESPACE).count { it.isNotEmpty() })
        }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
