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

package com.duckduckgo.vpn.internal.feature.trackers

import android.content.Context
import android.content.Intent
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.service.VpnStopReason
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.vpn.internal.feature.InternalFeatureReceiver
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * This receiver allows deletion of previously seen trackers.
 *
 * $ adb shell am broadcast -a delete-trackers
 */
class DeleteTrackersDebugReceiver(
    context: Context,
    receiver: (Intent) -> Unit,
) : InternalFeatureReceiver(context, receiver) {

    override fun intentAction(): String = ACTION

    companion object {
        private const val ACTION = "delete-trackers"

        fun createIntent(): Intent = Intent(ACTION)
    }
}

@ContributesMultibinding(AppScope::class)
class DeleteTrackersDebugReceiverRegister
@Inject
constructor(
    private val context: Context,
    private val vpnDatabase: VpnDatabase,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : VpnServiceCallbacks {
    private val className: String
        get() = DeleteTrackersDebugReceiver::class.java.simpleName

    private var receiver: DeleteTrackersDebugReceiver? = null

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        Timber.i("Debug receiver %s registered", className)

        receiver?.unregister()

        receiver =
            DeleteTrackersDebugReceiver(context) { _ ->
                appCoroutineScope.launch { vpnDatabase.vpnTrackerDao().deleteAllTrackers() }
            }
                .apply { register() }
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStopReason) {
        receiver?.unregister()
    }
}
