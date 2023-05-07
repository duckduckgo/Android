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

package com.duckduckgo.clicktoload.impl.handlers

import com.duckduckgo.clicktoload.impl.adapters.JSONObjectAdapter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject

class PlaceholderHandler @Inject constructor() {

    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    fun getMessageJson(action: String): String {
        val jsonAdapter: JsonAdapter<DisplayClickToLoadPlaceholders> = moshi.adapter(DisplayClickToLoadPlaceholders::class.java)
        return jsonAdapter.toJson(DisplayClickToLoadPlaceholders(options = Options(action = action))).toString()
    }

    data class DisplayClickToLoadPlaceholders(
        val type: String = "update",
        val feature: String = "clickToLoad",
        val messageType: String = "displayClickToLoadPlaceholders",
        val options: Options,
    )

    data class Options(
        val action: String,
    )
}
