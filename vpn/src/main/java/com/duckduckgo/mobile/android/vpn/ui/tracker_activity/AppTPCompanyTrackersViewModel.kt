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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity

import androidx.lifecycle.ViewModel
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

class AppTPCompanyTrackersViewModel
constructor(
    private val statsRepository: AppTrackerBlockingStatsRepository,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : ViewModel() {

    private val tickerChannel = MutableStateFlow(System.currentTimeMillis())

    suspend fun getTrackersForAppFromDate(date: String, packageName: String): Flow<ViewState> =
        withContext(dispatchers.io()) {
            return@withContext statsRepository
                .getTrackersForAppFromDate(date, packageName)
                .combine(tickerChannel.asStateFlow()) { trackers, _ -> trackers }
                .map { aggregateDataPerApp(it) }
                .flowOn(Dispatchers.Default)
        }

    private fun aggregateDataPerApp(trackerData: List<VpnTracker>): ViewState {
        val sourceData = mutableListOf<CompanyTrackingDetails>()

        val trackerCompany = trackerData.groupBy { it.trackerCompanyId }

        trackerCompany.forEach { data ->
            val trackerCompanyName = data.value[0].company
            val trackerCompanyDisplayName = data.value[0].companyDisplayName
            sourceData.add(
                CompanyTrackingDetails(
                    companyName = trackerCompanyName,
                    companyDisplayName = trackerCompanyDisplayName,
                    trackingAttempts = data.value.size))
        }

        return ViewState(trackerData.size, sourceData)
    }

    data class ViewState(
        val totalTrackingAttempts: Int,
        val trackingCompanies: List<CompanyTrackingDetails>
    )
    data class CompanyTrackingDetails(
        val companyName: String,
        val companyDisplayName: String,
        val trackingAttempts: Int
    )
}

@ContributesMultibinding(AppScope::class)
class AppTPCompanyTrackersViewModelFactory
@Inject
constructor(private val repositopryProvider: Provider<AppTrackerBlockingStatsRepository>) :
    ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(AppTPCompanyTrackersViewModel::class.java) ->
                    AppTPCompanyTrackersViewModel(repositopryProvider.get()) as T
                else -> null
            }
        }
    }
}
