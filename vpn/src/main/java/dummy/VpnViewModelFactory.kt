/*
 * Copyright (c) 2017 DuckDuckGo
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

package dummy

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import dummy.ui.VpnControllerViewModel
import dummy.ui.VpnPreferences
import javax.inject.Inject

@Suppress("UNCHECKED_CAST")
class VpnViewModelFactory @Inject constructor() : ViewModelProvider.NewInstanceFactory() {

    @Inject
    lateinit var applicationContext: Context

    @Inject
    lateinit var appTrackerBlockingStatsRepository: AppTrackerBlockingStatsRepository

    @Inject
    lateinit var vpnPreferences: VpnPreferences

    override fun <T : ViewModel> create(modelClass: Class<T>) =
        with(modelClass) {
            when {
                isAssignableFrom(VpnControllerViewModel::class.java) -> vpnControllerViewModel()
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        } as T

    private fun vpnControllerViewModel(): VpnControllerViewModel {
        return VpnControllerViewModel(
            applicationContext = applicationContext,
            repository = appTrackerBlockingStatsRepository,
            vpnPreferences = vpnPreferences
        )
    }

}
