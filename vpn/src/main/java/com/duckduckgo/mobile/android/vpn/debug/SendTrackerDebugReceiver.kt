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
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.BuildConfig
import com.duckduckgo.mobile.android.vpn.model.TrackingApp
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.service.VpnStopReason
import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.threeten.bp.LocalDateTime
import timber.log.Timber

/**
 * This receiver allows sending fake trackers, to do so, in the command line:
 *
 * $ adb shell am broadcast -a track --es times <N> --es hago <M>
 *
 * where `--es times <N>` is optional and is the number of trackers to be sent where `--es hago <M>`
 * is optional and is the timestamp (hours ago) for the trackers
 */
@ContributesMultibinding(scope = VpnScope::class, boundType = VpnServiceCallbacks::class)
@SingleInstanceIn(VpnScope::class)
class SendTrackerDebugReceiver
@Inject
constructor(
    private val context: Context,
    private val vpnDatabase: VpnDatabase,
) : BroadcastReceiver(), VpnServiceCallbacks {

    private fun register() {
        unregister()
        if (!BuildConfig.DEBUG) {
            Timber.i("Will not register SendTrackerDebugReceiver, not in DEBUG mode")
            return
        }

        Timber.i("Debug receiver SendTrackerDebugReceiver registered")
        context.registerReceiver(this, IntentFilter(INTENT_ACTION))
    }

    private fun unregister() {
        kotlin.runCatching { context.unregisterReceiver(this) }
    }

    fun handleIntent(intent: Intent) {
        GlobalScope.launch(Dispatchers.IO) {
            val times = intent.getStringExtra("times")?.toInt() ?: 1
            val hoursAgo = intent.getStringExtra("hago")?.toLong() ?: 0L
            Timber.i("Inserting %d trackers into the DB", times)

            val insertionList = mutableListOf<VpnTracker>()
            for (i in 0 until times) {
                insertionList.add(
                    dummyTrackers[(dummyTrackers.indices).shuffled().first()].copy(
                        timestamp =
                            DatabaseDateFormatter.timestamp(
                                LocalDateTime.now().minusHours(hoursAgo))))
            }
            vpnDatabase.vpnTrackerDao().insert(insertionList)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        handleIntent(intent)
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        Timber.v("Send tracker receiver started")
        register()
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStopReason) {
        Timber.v("Send tracker receiver stopped")
        unregister()
    }

    companion object {
        private const val INTENT_ACTION = "track"
    }
}

private val dummyTrackers =
    listOf(
        VpnTracker(
            trackerCompanyId = 0,
            domain = "www.facebook.com",
            company = "Facebook, Inc.",
            companyDisplayName = "Facebook",
            trackingApp = TrackingApp(packageId = "foo.package.id", appDisplayName = "Foo")),
        VpnTracker(
            trackerCompanyId = 0,
            domain = "api.segment.io",
            company = "Segment.io",
            companyDisplayName = "Segment",
            trackingApp = TrackingApp(packageId = "foo.package.id", appDisplayName = "Foo")),
        VpnTracker(
            trackerCompanyId = 0,
            domain = "crashlyticsreports-pa.googleapis.com",
            company = "Google LLC",
            companyDisplayName = "Google",
            trackingApp = TrackingApp(packageId = "foo.package.id", appDisplayName = "Foo")),
        VpnTracker(
            trackerCompanyId = 0,
            domain = "crashlyticsreports-pa.googleapis.com",
            company = "Google LLC",
            companyDisplayName = "Google",
            trackingApp = TrackingApp(packageId = "lion.package.id", appDisplayName = "LION")),
        VpnTracker(
            trackerCompanyId = 0,
            domain = "api.segment.io",
            company = "Segment.io",
            companyDisplayName = "Segment",
            trackingApp = TrackingApp(packageId = "lion.package.id", appDisplayName = "LION")),
        VpnTracker(
            trackerCompanyId = 0,
            domain = "crashlyticsreports-pa.googleapis.com",
            company = "Google LLC",
            companyDisplayName = "Google",
            trackingApp = TrackingApp(packageId = "puppy.package.id", appDisplayName = "PUPPY")),
        VpnTracker(
            trackerCompanyId = 0,
            domain = "api.segment.io",
            company = "Segment.io",
            companyDisplayName = "Segment",
            trackingApp = TrackingApp(packageId = "puppy.package.id", appDisplayName = "PUPPY")),
    )
