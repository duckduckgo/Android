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

package com.duckduckgo.mobile.android.vpn.feature.removal

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import timber.log.Timber

class VpnFeatureRemoverWorker(
    val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    lateinit var vpnFeatureRemover: VpnFeatureRemover

    override suspend fun doWork(): Result {
        Timber.d("VpnFeatureRemoverWorker, automatically removing AppTP feature")
        vpnFeatureRemover.scheduledRemoveFeature()
        return Result.success()
    }

    companion object {
        const val WORKER_VPN_FEATURE_REMOVER_TAG = "VpnFeatureRemoverTagWorker"
    }
}
