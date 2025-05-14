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

package com.duckduckgo.autofill.impl.service

import com.duckduckgo.app.browser.UriString.Companion.sameOrSubdomain
import com.duckduckgo.autofill.impl.service.store.AutofillServiceFeatureRepository
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface AutofillServiceExceptions {
    fun isAnException(domain: String): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealAutofillServiceExceptions @Inject constructor(
    private val repository: AutofillServiceFeatureRepository,
) : AutofillServiceExceptions {

    override fun isAnException(domain: String): Boolean {
        return repository.exceptions.any { sameOrSubdomain(domain, it) }
    }
}
