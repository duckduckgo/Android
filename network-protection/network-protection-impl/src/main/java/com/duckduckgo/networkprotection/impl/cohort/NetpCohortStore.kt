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

package com.duckduckgo.networkprotection.impl.cohort

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.state.NetPFeatureRemover
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

interface NetpCohortStore {
    var cohortLocalDate: LocalDate?
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = NetpCohortStore::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = NetPFeatureRemover.NetPStoreRemovalPlugin::class,
)
class RealNetpCohortStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : NetpCohortStore, NetPFeatureRemover.NetPStoreRemovalPlugin {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(
            FILENAME,
            multiprocess = true,
            migrate = false,
        )
    }

    override var cohortLocalDate: LocalDate?
        get() = preferences.getString(KEY_COHORT_LOCAL_DATE, null)?.let {
            LocalDate.parse(it)
        }
        set(localDate) {
            preferences.edit(commit = true) { putString(KEY_COHORT_LOCAL_DATE, formatter.format(localDate)) }
        }

    companion object {
        private const val FILENAME = "com.duckduckgo.networkprotection.cohort.prefs.v1"
        private const val KEY_COHORT_LOCAL_DATE = "KEY_COHORT_LOCAL_DATE"
    }

    override fun clearStore() {
        preferences.edit { clear() }
    }
}
