/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill

import androidx.core.net.toUri
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AutofillDomainFormatter {
    fun extractDomain(url: String?): String?
}

@ContributesBinding(FragmentScope::class)
class AutofillDomainFormatterDomainNameOnly @Inject constructor() : AutofillDomainFormatter {
    override fun extractDomain(url: String?): String? {
        val domain = url?.toUri()?.baseHost
        return if (domain.isNullOrBlank()) {
            null
        } else {
            domain
        }
    }
}
