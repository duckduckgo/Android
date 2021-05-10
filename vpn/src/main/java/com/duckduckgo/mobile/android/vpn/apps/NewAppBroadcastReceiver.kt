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

package com.duckduckgo.mobile.android.vpn.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.goAsync
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerRepository
import timber.log.Timber
import javax.inject.Inject

class NewAppBroadcastReceiver @Inject constructor(
    private val applicationContext: Context,
    private val appCategoryDetector: AppCategoryDetector,
    private val appTrackerRepository: AppTrackerRepository
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> intent.data?.schemeSpecificPart?.let { restartVpn(it) }
        }
    }

    private fun restartVpn(packageName: String) {
        Timber.d("Newly installed package $packageName")

        if (isGame(packageName) || isInExclusionList(packageName)) {
            Timber.i("Newly installed package $packageName is in exclusion list, disabling/renabling vpn")
            val pendingResult = goAsync()
            goAsync(pendingResult) {
                TrackerBlockingVpnService.restartVpnService(applicationContext)
            }
        } else {
            Timber.i("Newly installed package $packageName not in exclusion list")
        }
    }

    fun register() {
        IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addDataScheme("package")
        }.run {
            applicationContext.registerReceiver(this@NewAppBroadcastReceiver, this)
        }
    }

    private fun isGame(packageName: String): Boolean {
        return appCategoryDetector.getAppCategory(packageName) is AppCategory.Game
    }

    private fun isInExclusionList(packageName: String): Boolean {
        return appTrackerRepository.getAppExclusionList().contains(packageName)
    }
}
