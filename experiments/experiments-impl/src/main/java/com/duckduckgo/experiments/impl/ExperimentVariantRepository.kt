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

package com.duckduckgo.experiments.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.impl.store.ExperimentVariantDao
import com.duckduckgo.experiments.impl.store.ExperimentVariantEntity
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface ExperimentVariantRepository {
    fun updateVariants(variantEntityList: List<ExperimentVariantEntity>)
    fun getActiveVariants(): List<ExperimentVariantEntity>
}

@ContributesBinding(AppScope::class)
class ExperimentVariantRepositoryImpl @Inject constructor(
    private val experimentVariantDao: ExperimentVariantDao,
) : ExperimentVariantRepository {

    override fun updateVariants(variantEntityList: List<ExperimentVariantEntity>) {
        experimentVariantDao.delete()
        experimentVariantDao.insertAll(variantEntityList)
    }

    override fun getActiveVariants(): List<ExperimentVariantEntity> {
        return experimentVariantDao.variants().filter { it.weight != null && it.weight > 0.0 }.toList()
    }
}
