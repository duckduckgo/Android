/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.worker

import androidx.work.multiprocess.RemoteWorkerService
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.ProcessName
import com.duckduckgo.di.scopes.VpnScope
import dagger.android.AndroidInjection
import javax.inject.Inject
import logcat.logcat

@InjectWith(VpnScope::class)
class VpnRemoteWorkerService constructor() : RemoteWorkerService() {
    @Inject
    @ProcessName
    lateinit var processName: String

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
        logcat { "VPN-WORKER: running in process $processName" }
    }
}
