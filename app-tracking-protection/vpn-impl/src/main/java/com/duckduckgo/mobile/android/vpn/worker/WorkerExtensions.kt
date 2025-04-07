/*
 * Copyright (c) 2023 DuckDuckGo
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

import android.content.ComponentName
import androidx.work.Data
import androidx.work.PeriodicWorkRequest
import androidx.work.multiprocess.RemoteListenableWorker

/**
 * Use this extension function to make sure your [PeriodicWorkRequest] runs in the VPN process
 */
fun PeriodicWorkRequest.Builder.boundToVpnProcess(applicationId: String): PeriodicWorkRequest.Builder {
    val componentName = ComponentName(applicationId, VpnRemoteWorkerService::class.java.name)
    val data = Data.Builder()
        .putString(RemoteListenableWorker.ARGUMENT_PACKAGE_NAME, componentName.packageName)
        .putString(RemoteListenableWorker.ARGUMENT_CLASS_NAME, componentName.className)
        .build()

    return this.setInputData(data)
}
