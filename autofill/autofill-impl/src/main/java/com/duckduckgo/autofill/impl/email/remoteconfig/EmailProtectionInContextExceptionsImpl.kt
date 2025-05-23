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

package com.duckduckgo.autofill.impl.email.remoteconfig

import com.duckduckgo.app.browser.UriString.Companion.sameOrSubdomain
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface EmailProtectionInContextExceptions {
    fun isAnException(url: String): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class EmailProtectionInContextExceptionsImpl @Inject constructor(
    private val repository: EmailProtectionInContextFeatureRepository,
) : EmailProtectionInContextExceptions {

    override fun isAnException(url: String): Boolean {
        return repository.exceptions.any { sameOrSubdomain(url, it) }
    }
}
