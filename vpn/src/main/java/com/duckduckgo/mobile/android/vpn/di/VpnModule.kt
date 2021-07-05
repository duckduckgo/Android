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

import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import com.duckduckgo.di.scopes.VpnObjectGraph
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.*
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.*
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.DomainBasedTrackerDetector
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.VpnTrackerDetector
import com.duckduckgo.mobile.android.vpn.store.PacketPersister
import com.duckduckgo.mobile.android.vpn.store.RoomPacketPersister
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import java.nio.channels.Selector
import javax.inject.Named

@Module
@ContributesTo(VpnObjectGraph::class)
class VpnModule {

    @Provides
    @VpnScope
    @TargetApi(29)
    @Named("DetectOriginatingAppPackageModern")
    fun providesOriginatingAppResolverModern(connectivityManager: ConnectivityManager, packageManager: PackageManager): OriginatingAppPackageIdentifier {
        return DetectOriginatingAppPackageModern(connectivityManager, packageManager)
    }

    @Provides
    @VpnScope
    @Named("DetectOriginatingAppPackageLegacy")
    fun providesOriginatingAppResolverLegacy(
        packageManager: PackageManager,
        networkFileConnectionMatcher: NetworkFileConnectionMatcher,
        @VpnCoroutineScope vpnCoroutineScope: CoroutineScope
    ): OriginatingAppPackageIdentifier {
        return DetectOriginatingAppPackageLegacy(packageManager, networkFileConnectionMatcher, vpnCoroutineScope)
    }

    @VpnScope
    @Provides
    fun providesProcNetFileConnectionMatcher(): NetworkFileConnectionMatcher {
        return ProcNetFileConnectionMatcher()
    }

    @Provides
    @VpnScope
    fun providesAppNameResolver(packageManager: PackageManager): AppNameResolver = AppNameResolver(packageManager)

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
        deviceShieldPixels: DeviceShieldPixels,
        hostnameExtractor: HostnameExtractor,
        appTrackerRepository: AppTrackerRepository,
        vpnDatabase: VpnDatabase
    ): VpnTrackerDetector {
        return DomainBasedTrackerDetector(deviceShieldPixels, hostnameExtractor, appTrackerRepository, vpnDatabase)
    }

    @VpnScope
    @Provides
    @TcpNetworkSelector
    fun provideTcpNetworkSelector(): Selector {
        return Selector.open()
    }
}
