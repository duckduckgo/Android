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

package com.duckduckgo.app.dev.settings.privacy

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.trackerdetection.api.TrackerDataDownloader
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.extensions.registerNotExportedReceiver
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import logcat.LogPriority.INFO
import logcat.logcat
import javax.inject.Inject

class TrackerDataDevReceiver(
    context: Context,
    intentAction: String = DOWNLOAD_TDS_INTENT_ACTION,
    private val receiver: (Intent) -> Unit,
) : BroadcastReceiver() {
    init {
        context.registerNotExportedReceiver(this, IntentFilter(intentAction))
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        receiver(intent)
    }

    companion object {
        const val DOWNLOAD_TDS_INTENT_ACTION = "downloadTds"
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class TrackerDataDevReceiverRegister @Inject constructor(
    private val context: Context,
    private val trackderDataDownloader: TrackerDataDownloader,
    private val appBuildConfig: AppBuildConfig,
) : MainProcessLifecycleObserver {

    @SuppressLint("CheckResult")
    override fun onCreate(owner: LifecycleOwner) {
        if (!appBuildConfig.isDebug) {
            logcat(INFO) { "Will not register TrackerDataDevReceiverRegister, not in DEBUG mode" }
            return
        }

        logcat(INFO) { "Debug receiver TrackerDataDevReceiverRegister registered" }

        TrackerDataDevReceiver(context) { _ ->
            trackderDataDownloader.downloadTds()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { Toast.makeText(context, "Tds data downloaded", Toast.LENGTH_LONG).show() },
                    { Toast.makeText(context, "Error while downloading Tds ${it.localizedMessage}", Toast.LENGTH_LONG).show() },
                )
        }
    }
}
