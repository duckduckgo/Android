/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.di

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import com.duckduckgo.di.scopes.VpnObjectGraph
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.*
import com.duckduckgo.mobile.android.vpn.processor.tcp.requestingapp.OriginatingAppResolver
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.DomainBasedTrackerDetector
import com.duckduckgo.mobile.android.vpn.trackers.TrackerListProvider
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.VpnTrackerDetector
import com.duckduckgo.mobile.android.vpn.store.PacketPersister
import com.duckduckgo.mobile.android.vpn.store.RoomPacketPersister
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dummy.ui.VpnPreferences

@Module
@ContributesTo(VpnObjectGraph::class)
class VpnModule {

    @Provides
    fun providesOriginatingAppResolver(connectivityManager: ConnectivityManager, packageManager: PackageManager): OriginatingAppResolver {
        return OriginatingAppResolver(connectivityManager, packageManager)
    }

    @Provides
    fun providesDispatcherProvider(): VpnDispatcherProvider {
        return DefaultVpnDispatcherProvider()
    }

    @Provides
    fun provideNameHeaderExtractor(): HostnameHeaderExtractor = PlaintextHostHeaderExtractor()

    @Provides
    fun provideEncryptedRequestHostExtractor(): EncryptedRequestHostExtractor = ServerNameIndicationHeaderHostExtractor()

    @Provides
    fun providePayloadBytesExtractor(): PayloadBytesExtractor = PayloadBytesExtractor()

    @Provides
    fun provideTrackerListProvider(): TrackerListProvider = TrackerListProvider()

    @Provides
    fun provideAndroidHostnameExtractor(
        hostnameHeaderExtractor: HostnameHeaderExtractor,
        encryptedRequestHostExtractor: EncryptedRequestHostExtractor,
        payloadBytesExtractor: PayloadBytesExtractor
    ): HostnameExtractor {
        return AndroidHostnameExtractor(hostnameHeaderExtractor, encryptedRequestHostExtractor, payloadBytesExtractor)
    }

    @VpnScope
    @Provides
    fun providesPacketPersister(vpnDatabase: VpnDatabase): PacketPersister {
        return RoomPacketPersister(vpnDatabase)
    }

    @VpnScope
    @Provides
    fun providesVpnTrackerDetector(
        hostnameExtractor: HostnameExtractor,
        trackerListProvider: TrackerListProvider,
        vpnDatabase: VpnDatabase
    ): VpnTrackerDetector {
        return DomainBasedTrackerDetector(hostnameExtractor, trackerListProvider, vpnDatabase)
    }

    @VpnScope
    @Provides
    fun providesVpnPreferences(context: Context): VpnPreferences {
        return VpnPreferences(context)
    }
}
