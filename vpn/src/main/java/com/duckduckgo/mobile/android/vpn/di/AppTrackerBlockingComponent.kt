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

import android.app.Application
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.VpnTrackerDetector
import com.duckduckgo.mobile.android.vpn.store.PacketPersister
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import dagger.BindsInstance
import dagger.Component

@Component(modules = [VpnDatabaseModule::class, VpnTrackerDetectorModule::class])
interface AppTrackerBlockingComponent {

    fun getVpnDatabase(): VpnDatabase

    fun getVpnTrackerDetector(): VpnTrackerDetector

    fun getPacketPersister(): PacketPersister

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: Application): AppTrackerBlockingComponent.Builder

        fun build(): AppTrackerBlockingComponent
    }
}
