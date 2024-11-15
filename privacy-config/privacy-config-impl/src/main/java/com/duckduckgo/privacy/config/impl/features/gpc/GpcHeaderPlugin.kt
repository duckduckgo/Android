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

package com.duckduckgo.privacy.config.impl.features.gpc

import com.duckduckgo.common.utils.plugins.headers.CustomHeadersProvider.CustomHeadersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.Gpc
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(scope = AppScope::class)
class GpcHeaderPlugin @Inject constructor(
    private val gpc: Gpc,
) : CustomHeadersPlugin {

    override fun getHeaders(url: String): Map<String, String> {
        return gpc.getHeaders(url)
    }
}
