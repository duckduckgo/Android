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
import com.duckduckgo.experiments.impl.VariantManagerImpl.Companion.DEFAULT_VARIANT
import com.duckduckgo.experiments.impl.reinstalls.REINSTALL_VARIANT
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority.INFO
import logcat.logcat
import javax.inject.Inject

interface ExperimentVariantRepository {
    fun getUserVariant(): String?
    fun updateVariant(variantKey: String)
    fun getAppReferrerVariant(): String?
    fun updateAppReferrerVariant(variant: String)
}

@ContributesBinding(AppScope::class)
class ExperimentVariantRepositoryImpl @Inject constructor(
    private val store: StatisticsDataStore,
) : ExperimentVariantRepository {

    override fun getUserVariant(): String? = store.variant

    override fun updateVariant(variantKey: String) {
        logcat(INFO) { "Updating variant for user: $variantKey" }
        if (updateVariantIsAllowed()) {
            store.variant = variantKey
        }
    }

    private fun updateVariantIsAllowed(): Boolean {
        return store.variant != DEFAULT_VARIANT.key && store.variant != REINSTALL_VARIANT
    }

    override fun getAppReferrerVariant(): String? = store.referrerVariant

    override fun updateAppReferrerVariant(variant: String) {
        logcat(INFO) { "Updating variant for app referer: $variant" }
        store.variant = variant
        store.referrerVariant = variant
    }
}
