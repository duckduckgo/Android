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
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.*
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.*
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.AppTrackerRecorder
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.DomainBasedTrackerDetector
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.VpnTrackerDetector
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.VpnTrackerDetectorInterceptor
import com.duckduckgo.mobile.android.vpn.store.*
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import java.nio.channels.Selector
import javax.inject.Named

@Module
@ContributesTo(VpnScope::class)
class VpnModule {

    @Provides
    @SingleInstanceIn(VpnScope::class)
    @TargetApi(29)
    @Named("DetectOriginatingAppPackageModern")
    fun providesOriginatingAppResolverModern(
        connectivityManager: ConnectivityManager,
        packageManager: PackageManager
    ): OriginatingAppPackageIdentifier {
        return DetectOriginatingAppPackageModern(connectivityManager, packageManager)
    }

    @Provides
    @SingleInstanceIn(VpnScope::class)
    @Named("DetectOriginatingAppPackageLegacy")
    fun providesOriginatingAppResolverLegacy(
        packageManager: PackageManager,
        networkFileConnectionMatcher: NetworkFileConnectionMatcher,
        @VpnCoroutineScope vpnCoroutineScope: CoroutineScope
    ): OriginatingAppPackageIdentifier {
        return DetectOriginatingAppPackageLegacy(packageManager, networkFileConnectionMatcher, vpnCoroutineScope)
    }

    @SingleInstanceIn(VpnScope::class)
    @Provides
    fun providesProcNetFileConnectionMatcher(): NetworkFileConnectionMatcher {
        return ProcNetFileConnectionMatcher()
    }

    @SingleInstanceIn(VpnScope::class)
    @Provides
    fun providesAppNameResolver(packageManager: PackageManager): AppNameResolver = AppNameResolver(packageManager)

    @Provides
    fun providesDispatcherProvider(): VpnDispatcherProvider {
        return DefaultVpnDispatcherProvider()
    }

    @Provides
    fun provideNameHeaderExtractor(): HostnameHeaderExtractor = PlaintextHostHeaderExtractor()

    @Provides
    fun provideEncryptedRequestHostExtractor(tlsMessageDetector: TlsMessageDetector): EncryptedRequestHostExtractor =
        ServerNameIndicationHeaderHostExtractor(tlsMessageDetector)

    @Provides
    fun providePayloadBytesExtractor(): PayloadBytesExtractor = ConcretePayloadBytesExtractor()

    @Provides
    fun provideAndroidHostnameExtractor(
        hostnameHeaderExtractor: HostnameHeaderExtractor,
        encryptedRequestHostExtractor: EncryptedRequestHostExtractor
    ): HostnameExtractor {
        return AndroidHostnameExtractor(hostnameHeaderExtractor, encryptedRequestHostExtractor)
    }

    @SingleInstanceIn(VpnScope::class)
    @Provides
    fun providesPacketPersister(
        vpnDatabase: VpnDatabase,
        appBuildConfig: AppBuildConfig
    ): PacketPersister {
        return if (appBuildConfig.isDebug) {
            RoomPacketPersister(vpnDatabase)
        } else {
            DummyPacketPersister()
        }
    }

    @SingleInstanceIn(VpnScope::class)
    @Provides
    fun providesVpnTrackerDetector(
        hostnameExtractor: HostnameExtractor,
        appTrackerRepository: AppTrackerRepository,
        appTrackerRecorder: AppTrackerRecorder,
        payloadBytesExtractor: PayloadBytesExtractor,
        vpnDatabase: VpnDatabase,
        tlsContentTypeExtractor: ContentTypeExtractor,
        requestInterceptors: PluginPoint<VpnTrackerDetectorInterceptor>,
    ): VpnTrackerDetector {
        return DomainBasedTrackerDetector(
            hostnameExtractor,
            appTrackerRepository,
            appTrackerRecorder,
            payloadBytesExtractor,
            tlsContentTypeExtractor,
            vpnDatabase,
            requestInterceptors
        )
    }

    @SingleInstanceIn(VpnScope::class)
    @Provides
    fun providesContentTypeExtractor(tlsMessageDetector: TlsMessageDetector): ContentTypeExtractor {
        return TlsContentTypeExtractor(tlsMessageDetector)
    }

    @SingleInstanceIn(VpnScope::class)
    @Provides
    @TcpNetworkSelector
    fun provideTcpNetworkSelector(): Selector {
        return Selector.open()
    }
}
