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
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.AndroidHostnameExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.EncryptedRequestHostExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.HostnameExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.HostnameHeaderExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.PayloadBytesExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.PlaintextHostHeaderExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.ServerNameIndicationHeaderHostExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.TrackerListProvider
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class TrackerDetectorModule {

    @Provides
    fun providesDispatcherProvider(): VpnDispatcherProvider {
        return DefaultVpnDispatcherProvider()
    }

    @Provides
    @Singleton
    fun providesNotificationManager(context: Context): NotificationManagerCompat {
        return NotificationManagerCompat.from(context)
    }

    @Provides
    fun provideNameHeaderExtractor(): HostnameHeaderExtractor {
        return PlaintextHostHeaderExtractor()
    }

    @Provides
    fun provideEncryptedRequestHostExtractor(): EncryptedRequestHostExtractor {
        return ServerNameIndicationHeaderHostExtractor()
    }

    @Provides
    fun providePayloadBytesExtractor(): PayloadBytesExtractor {
        return PayloadBytesExtractor()
    }

    @Provides
    fun provideTrackerListProvider(): TrackerListProvider {
        return TrackerListProvider()
    }

    @Provides
    fun provideAndroidHostnameExtractor(
        hostnameHeaderExtractor: HostnameHeaderExtractor,
        encryptedRequestHostExtractor: EncryptedRequestHostExtractor,
        payloadBytesExtractor: PayloadBytesExtractor
    ): HostnameExtractor {
        return AndroidHostnameExtractor(hostnameHeaderExtractor, encryptedRequestHostExtractor, payloadBytesExtractor)
    }

}