/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.searchengines

import com.duckduckgo.common.utils.AppUrl
import java.net.MalformedURLException
import java.net.URL
import kotlin.jvm.Throws

sealed class SearchEngine {
    abstract val host: String
    abstract val home: String
    abstract val autocomplete: String
}

data object DuckDuckGoSearchEngine : SearchEngine() {
    override val host: String = AppUrl.Url.HOST
    override val home: String = AppUrl.Url.HOME
    override val autocomplete: String = AppUrl.Url.API + "/ac/"
}

class SearxSearchEngine @Throws(MalformedURLException::class) constructor(override val home: String) : SearchEngine() {
    override val host: String = URL(home).host
    override val autocomplete: String = if (home.endsWith("/")) "${home}autocompleter" else "$home/autocompleter"
}
