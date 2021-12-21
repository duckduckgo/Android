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
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.service.VpnStopReason
import com.duckduckgo.mobile.android.vpn.service.goAsync
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import timber.log.Timber

@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(scope = VpnScope::class, boundType = VpnServiceCallbacks::class)
class NewAppBroadcastReceiver
@Inject
constructor(
    private val applicationContext: Context,
    private val appCategoryDetector: AppCategoryDetector,
    private val appTrackerRepository: AppTrackerRepository,
    private val dispatcherProvider: DispatcherProvider
) : BroadcastReceiver(), VpnServiceCallbacks {

    @MainThread
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> intent.data?.schemeSpecificPart?.let { restartVpn(it) }
        }
    }

    private fun restartVpn(packageName: String) {
        Timber.d("Newly installed package $packageName")

        val pendingResult = goAsync()
        goAsync(pendingResult) {
            if (isGame(packageName) || isInExclusionList(packageName)) {
                Timber.i(
                    "Newly installed package $packageName is in exclusion list, disabling/renabling vpn")
                TrackerBlockingVpnService.restartVpnService(applicationContext)
            } else {
                Timber.i("Newly installed package $packageName not in exclusion list")
            }
        }
    }

    private fun register() {
        kotlin.runCatching { applicationContext.unregisterReceiver(this) }

        IntentFilter()
            .apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addDataScheme("package")
            }
            .run { applicationContext.registerReceiver(this@NewAppBroadcastReceiver, this) }
    }

    private fun unregister() {
        kotlin.runCatching { applicationContext.unregisterReceiver(this) }
    }

    private fun isGame(packageName: String): Boolean {
        return appCategoryDetector.getAppCategory(packageName) is AppCategory.Game
    }

    @WorkerThread
    private suspend fun isInExclusionList(packageName: String): Boolean =
        withContext(dispatcherProvider.io()) {
            return@withContext appTrackerRepository.getAppExclusionList().any {
                it.packageId == packageName
            }
        }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        Timber.v("New app receiver started")
        register()
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStopReason) {
        Timber.v("New app receiver stopped")
        unregister()
    }
}
