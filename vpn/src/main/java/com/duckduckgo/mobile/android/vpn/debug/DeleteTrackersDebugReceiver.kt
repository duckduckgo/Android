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

package com.duckduckgo.mobile.android.vpn.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.duckduckgo.di.scopes.VpnObjectGraph
import com.duckduckgo.mobile.android.vpn.BuildConfig
import com.duckduckgo.mobile.android.vpn.di.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.service.VpnStopReason
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * This receiver allows deletion of previously seen trackers.
 *
 * $ adb shell am broadcast -a delete-trackers
 *
 */
@ContributesMultibinding(
    scope = VpnObjectGraph::class,
    boundType = VpnServiceCallbacks::class
)
@VpnScope
class DeleteTrackersDebugReceiver @Inject constructor(
    private val context: Context,
    private val vpnDatabase: VpnDatabase,
) : BroadcastReceiver(), VpnServiceCallbacks {

    private val className: String
        get() = DeleteTrackersDebugReceiver::class.java.simpleName

    private fun register() {
        unregister()
        if (!BuildConfig.DEBUG) {
            Timber.i("Will not register %s, not in DEBUG mode", className)
            return
        }

        Timber.i("Debug receiver %s registered", className)
        context.registerReceiver(this, IntentFilter(INTENT_ACTION))
    }

    private fun unregister() {
        kotlin.runCatching { context.unregisterReceiver(this) }
    }

    fun handleIntent(intent: Intent) {
        GlobalScope.launch(Dispatchers.IO) {
            Timber.i("Deleting all trackers from the DB")
            vpnDatabase.vpnTrackerDao().deleteAllTrackers()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        handleIntent(intent)
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        Timber.v("Debug receiver started: %s", className)
        register()
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStopReason) {
        Timber.v("Debug receiver stopped: %s", className)
        unregister()
    }

    companion object {
        private const val INTENT_ACTION = "delete-trackers"
    }
}

