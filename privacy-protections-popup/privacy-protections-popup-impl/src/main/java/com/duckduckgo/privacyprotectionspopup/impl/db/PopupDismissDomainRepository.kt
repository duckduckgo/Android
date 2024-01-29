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

package com.duckduckgo.privacyprotectionspopup.impl.db

import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.threeten.bp.Instant

interface PopupDismissDomainRepository {
    fun getPopupDismissTime(domain: String): Flow<Instant?>
    suspend fun setPopupDismissTime(domain: String, time: Instant)
}

@ContributesBinding(FragmentScope::class)
class PopupDismissDomainRepositoryImpl @Inject constructor(
    private val dao: PopupDismissDomainsDao,
) : PopupDismissDomainRepository {

    override fun getPopupDismissTime(domain: String): Flow<Instant?> =
        dao.query(domain).map { it?.dismissedAt }

    override suspend fun setPopupDismissTime(
        domain: String,
        time: Instant,
    ) {
        dao.insert(PopupDismissDomain(domain = domain, dismissedAt = time))
    }
}
