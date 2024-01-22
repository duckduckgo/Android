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

import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.VariantConfig
import com.duckduckgo.experiments.impl.store.ExperimentVariantDao
import com.duckduckgo.experiments.impl.store.ExperimentVariantEntity
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import timber.log.Timber

interface ExperimentVariantRepository {
    fun saveVariants(variants: List<VariantConfig>)
    fun getActiveVariants(): List<ExperimentVariantEntity>
    fun getUserVariant(): String?
    fun updateVariant(variantKey: String)
    fun getAppReferrerVariant(): String?
    fun updateAppReferrerVariant(variant: String)
}

@ContributesBinding(AppScope::class)
class ExperimentVariantRepositoryImpl @Inject constructor(
    private val experimentVariantDao: ExperimentVariantDao,
    private val store: StatisticsDataStore,
) : ExperimentVariantRepository {

    override fun saveVariants(variants: List<VariantConfig>) {
        val variantEntityList: MutableList<ExperimentVariantEntity> = mutableListOf()
        variants.map {
            variantEntityList.add(
                ExperimentVariantEntity(
                    key = it.variantKey,
                    weight = it.weight,
                    localeFilter = it.filters?.locale.orEmpty(),
                    androidVersionFilter = it.filters?.androidVersion.orEmpty(),
                ),
            )
        }
        experimentVariantDao.delete()
        experimentVariantDao.insertAll(variantEntityList)
    }

    override fun getActiveVariants(): List<ExperimentVariantEntity> {
        return experimentVariantDao.variants()
    }

    override fun getUserVariant(): String? = store.variant

    override fun updateVariant(variantKey: String) {
        Timber.i("Updating variant for user: $variantKey")
        store.variant = variantKey
    }

    override fun getAppReferrerVariant(): String? = store.referrerVariant

    override fun updateAppReferrerVariant(variant: String) {
        Timber.i("Updating variant for app referer: $variant")
        store.variant = variant
        store.referrerVariant = variant
    }
}
