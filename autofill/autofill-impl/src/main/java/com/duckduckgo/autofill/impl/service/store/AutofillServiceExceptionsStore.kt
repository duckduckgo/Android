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

package com.duckduckgo.autofill.impl.service.store

import com.duckduckgo.autofill.impl.service.AutofillServiceFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureExceptions
import com.duckduckgo.feature.toggles.api.FeatureExceptions.FeatureException
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@RemoteFeatureStoreNamed(AutofillServiceFeature::class)
class AutofillServiceExceptionsStore @Inject constructor(
    private val autofillFeatureRepository: AutofillFeatureRepository,
) : FeatureExceptions.Store {
    override fun insertAll(exception: List<FeatureException>) {
        autofillFeatureRepository.insertAll(
            exception.map { AutofillServiceException(domain = it.domain, reason = it.reason.orEmpty()) },
        )
    }
}
