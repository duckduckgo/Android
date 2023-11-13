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

package com.duckduckgo.privacy.config.impl.features.drm

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureExceptions
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.duckduckgo.privacy.config.store.DrmBlockExceptionEntity
import com.duckduckgo.privacy.config.store.features.drm.DrmBlockRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@RemoteFeatureStoreNamed(DrmBlockFeature::class)
class DrmBlockFeatureExceptionStore @Inject constructor(
    private val drmBlockRepository: DrmBlockRepository,
) : FeatureExceptions.Store {
    override fun insertAll(exception: List<FeatureExceptions.FeatureException>) {
        drmBlockRepository.updateAll(
            exception.map { DrmBlockExceptionEntity(domain = it.domain) },
        )
    }
}
