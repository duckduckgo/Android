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

package com.duckduckgo.mobile.android.vpn.cohort

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.di.scopes.VpnObjectGraph
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.service.VpnStopReason
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.SingleInstanceIn
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import javax.inject.Inject

interface CohortStore {
    /**
     * @return the stored cohort local date or [null] if never set
     */
    fun getCohortStoredLocalDate(): LocalDate?

    /**
     * Stores the cohort [LocalDate] passed as parameter
     */
    fun setCohortLocalDate(localDate: LocalDate)
}

@ContributesBinding(
    scope = AppObjectGraph::class,
    boundType = CohortStore::class
)
class RealCohortStore @Inject constructor(
    private val context: Context
) : CohortStore, VpnServiceCallbacks {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    @VisibleForTesting
    val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_MULTI_PROCESS)

    override fun getCohortStoredLocalDate(): LocalDate? {
        return preferences.getString(KEY_COHORT_LOCAL_DATE, null)?.let {
            LocalDate.parse(it)
        }
    }

    override fun setCohortLocalDate(localDate: LocalDate) {
        preferences.edit { putString(KEY_COHORT_LOCAL_DATE, formatter.format(localDate)) }
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        // skip if already stored
        getCohortStoredLocalDate()?.let { return }

        setCohortLocalDate(LocalDate.now())
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStopReason) {
        // noop
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.mobile.atp.cohort.prefs"
        private const val KEY_COHORT_LOCAL_DATE = "KEY_COHORT_LOCAL_DATE"
    }
}

@Module
@ContributesTo(VpnObjectGraph::class)
abstract class CohortStoreModule {
    @Binds
    @IntoSet
    @SingleInstanceIn(VpnObjectGraph::class)
    abstract fun bindCohortStore(realCohortStore: RealCohortStore): VpnServiceCallbacks
}
