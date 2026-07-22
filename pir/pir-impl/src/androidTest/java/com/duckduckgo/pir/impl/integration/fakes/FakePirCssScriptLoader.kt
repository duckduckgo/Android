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

package com.duckduckgo.pir.impl.integration.fakes

import com.duckduckgo.pir.impl.scripts.PirCssScriptLoader

/**
 * Fake PirCssScriptLoader for integration tests that returns an empty script since FakePirMessagingInterface handles all JS responses.
 */
class FakePirCssScriptLoader : PirCssScriptLoader {
    override suspend fun getScript(): String = ""
    override val secret: String
        get() = "secret"
    override val javascriptInterface: String
        get() = "javascriptInterface"
    override val callbackName: String
        get() = "callbackName"
}
