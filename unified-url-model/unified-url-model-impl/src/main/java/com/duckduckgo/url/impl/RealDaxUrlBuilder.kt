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
import com.duckduckgo.url.DaxScheme
import com.duckduckgo.url.DaxUrl
import com.duckduckgo.url.DaxUrlBuilder

internal class RealDaxUrlBuilder(
    private val memoize: Boolean,
) : DaxUrlBuilder {

    private val builder = Uri.Builder()

    override fun scheme(scheme: DaxScheme): DaxUrlBuilder = apply {
        builder.scheme(scheme.value.takeIf { it.isNotEmpty() })
    }

    override fun encodedHost(host: String?): DaxUrlBuilder = apply { builder.encodedAuthority(host) }

    override fun encodedPath(path: String?): DaxUrlBuilder = apply { builder.encodedPath(path) }

    override fun encodedQuery(query: String?): DaxUrlBuilder = apply { builder.encodedQuery(query) }

    override fun build(): DaxUrl = RealDaxUrl(builder.build().toString(), memoize)
}
