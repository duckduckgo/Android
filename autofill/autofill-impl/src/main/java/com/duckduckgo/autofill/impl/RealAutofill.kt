/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.autofill.impl

import com.duckduckgo.app.browser.UriString.Companion.sameOrSubdomain
import com.duckduckgo.autofill.api.Autofill
import com.duckduckgo.autofill.impl.feature.plugin.AutofillFeatureRepository
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealAutofill @Inject constructor(
    private val autofillRepository: AutofillFeatureRepository,
    private val unprotectedTemporary: UnprotectedTemporary,
) : Autofill {

    override fun isAnException(url: String): Boolean {
        return unprotectedTemporary.isAnException(url) || matches(url)
    }

    private fun matches(url: String): Boolean {
        return autofillRepository.exceptions.any { sameOrSubdomain(url, it.domain) }
    }
}
