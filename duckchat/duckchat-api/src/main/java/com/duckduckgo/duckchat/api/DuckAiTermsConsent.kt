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
package com.duckduckgo.duckchat.api

import com.duckduckgo.browsermode.api.BrowserMode
import org.json.JSONObject

/**
 * Sending a prompt from the native input is how the user accepts the Duck.ai terms. Every place that builds a prompt
 * for the page goes through here, so the page is told and native storage is updated whichever surface was used.
 */
interface DuckAiTermsConsent {
    /**
     * When native ToS consent is on, marks [params] as carrying the user's acceptance and records it in native storage.
     * Does nothing otherwise, which leaves the payload exactly as it was.
     */
    fun carry(
        params: JSONObject,
        browserMode: BrowserMode,
    )
}
