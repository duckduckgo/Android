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

package com.duckduckgo.pir.internal.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.pir.internal.R
import com.duckduckgo.pir.internal.databinding.ActivityPirInternalSettingsBinding
import com.duckduckgo.pir.internal.scan.PirForegroundScanService
import com.duckduckgo.pir.internal.store.PirRepository
import com.duckduckgo.pir.internal.store.PirRepository.ScanResult
import com.duckduckgo.pir.internal.store.PirRepository.ScanResult.ErrorResult
import com.duckduckgo.pir.internal.store.PirRepository.ScanResult.ExtractedProfileResult
import com.duckduckgo.pir.internal.store.PirRepository.ScanResult.NavigateResult
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirSettingsScreenNoParams::class)
class PirDevSettings : DuckDuckGoActivity() {
    @Inject
    lateinit var repository: PirRepository

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var notificationManagerCompat: NotificationManagerCompat

    private val binding: ActivityPirInternalSettingsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)
        setupViews()
        createNotificationChannel()
        bindViews()
    }

    private fun bindViews() {
        repository.getAllResultsFlow()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach {
                render(it)
            }
            .launchIn(lifecycleScope)
    }

    private fun render(results: List<ScanResult>) {
        val extractedCount = results.filterIsInstance<ExtractedProfileResult>().size
        val errorCount = results.filterIsInstance<ErrorResult>().size
        binding.debugStatus.setSecondaryText(getString(R.string.pirStatsStatus, extractedCount, errorCount))

        val stringBuilder = StringBuilder()
        results.forEach {
            stringBuilder.append("BROKER NAME: ${it.brokerName}\nACTION EXECUTED: ${it.actionType}\n")
            when (it) {
                is NavigateResult -> {
                    stringBuilder.append("URL TO NAVIGATE: ${it.url}\n")
                }

                is ExtractedProfileResult -> {
                    stringBuilder.append("PROFILE QUERY:\n ${it.profileQuery} \n")
                    stringBuilder.append("EXTRACTED DATA:\n")
                    it.extractResults.forEach { extract ->
                        stringBuilder.append("> ${extract.scrapedData.profileUrl?.profileUrl} \n")
                    }
                }

                is ErrorResult -> {
                    stringBuilder.append("*ERROR ENCOUNTERED: ${it.message}\n")
                }
            }
            stringBuilder.append("--------------------------\n")
        }
        binding.simpleScanResults.text = stringBuilder.toString()
    }

    private fun setupViews() {
        binding.debugRunScan.setOnClickListener {
            notificationManagerCompat.cancel(NOTIF_ID_STATUS_COMPLETE)
            logcat { "PIR-SCAN: Attempting to start PirForegroundScanService from ${Process.myPid()}" }
            startForegroundService(Intent(this, PirForegroundScanService::class.java))
        }
        binding.debugForceKill.setOnClickListener {
            stopService(Intent(this, PirForegroundScanService::class.java))
            lifecycleScope.launch(dispatcherProvider.io()) {
                repository.deleteAllResults()
            }
            notificationManagerCompat.cancel(NOTIF_ID_STATUS_COMPLETE)
        }

        lifecycleScope.launch(dispatcherProvider.io()) {
            val toProcess = repository.getAllBrokersForScan().size
            withContext(dispatcherProvider.main()) {
                binding.debugTotalToProcess.setSecondaryText("" + toProcess)
            }
        }
    }

    private fun createNotificationChannel() {
        // Define the importance level of the notification channel
        val importance = NotificationManager.IMPORTANCE_DEFAULT

        // Create the NotificationChannel with a unique ID, name, and importance level
        val channel =
            NotificationChannel(NOTIF_CHANNEL_ID, "Pir Dev Notifications", importance)
        channel.description = "Notifications for Pir Dev"

        // Register the channel with the system
        val notificationManager = getSystemService(
            NotificationManager::class.java,
        )
        notificationManager?.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIF_CHANNEL_ID = "PirDevNotificationChannel"
        const val NOTIF_ID_STATUS_COMPLETE = 987
    }
}

object PirSettingsScreenNoParams : ActivityParams
