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

package com.duckduckgo.privacy.config.impl.features.https

import com.duckduckgo.app.global.UriString.Companion.sameOrSubdomain
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.Https
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.https.HttpsRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealHttps
@Inject
constructor(
    private val httpsRepository: HttpsRepository,
    private val unprotectedTemporary: UnprotectedTemporary
) : Https {

    override fun isAnException(url: String): Boolean {
        return unprotectedTemporary.isAnException(url) || matches(url)
    }

    private fun matches(url: String): Boolean {
        return httpsRepository.exceptions.any { sameOrSubdomain(url, it.domain) }
    }
}
