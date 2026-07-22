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

package com.duckduckgo.url.impl

import android.net.Uri
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.url.DaxUrl

/**
 * More powerful variant: memoizes the parsed host of [domain] once so repeated [matches] calls
 * don't re-parse the parent (the expensive part of the current hot path). Future work extends the
 * memoization to eTLD+1.
 */
class NewDaxUrl(override val domain: String) : DaxUrl {

    private val parentHost: String? by lazy(LazyThreadSafetyMode.PUBLICATION) { Uri.parse(domain).baseHost }

    override fun matches(url: String): Boolean {
        val parent = parentHost ?: return false
        val childHost = Uri.parse(url).baseHost ?: return false
        return parent == childHost || childHost.endsWith(".$parent")
    }
}
