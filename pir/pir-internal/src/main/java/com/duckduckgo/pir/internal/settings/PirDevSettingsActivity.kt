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
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.multiprocess.RemoteListenableWorker
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.pir.internal.R
import com.duckduckgo.pir.internal.databinding.ActivityPirInternalSettingsBinding
import com.duckduckgo.pir.internal.pixels.PirPixelSender
import com.duckduckgo.pir.internal.service.PirForegroundScanService
import com.duckduckgo.pir.internal.service.PirRemoteWorkerService
import com.duckduckgo.pir.internal.service.PirScheduledScanRemoteWorker
import com.duckduckgo.pir.internal.service.PirScheduledScanRemoteWorker.Companion.TAG_SCHEDULED_SCAN
import com.duckduckgo.pir.internal.store.PirRepository
import com.duckduckgo.pir.internal.store.PirRepository.ScanResult
import com.duckduckgo.pir.internal.store.PirRepository.ScanResult.ErrorResult
import com.duckduckgo.pir.internal.store.PirRepository.ScanResult.ExtractedProfileResult
import com.duckduckgo.pir.internal.store.db.Address
import com.duckduckgo.pir.internal.store.db.PirScanLog
import com.duckduckgo.pir.internal.store.db.ScanEventType.SCHEDULED_SCAN_SCHEDULED
import com.duckduckgo.pir.internal.store.db.UserName
import com.duckduckgo.pir.internal.store.db.UserProfile
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.logcat

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirSettingsScreenNoParams::class)
class PirDevSettingsActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var repository: PirRepository

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var notificationManagerCompat: NotificationManagerCompat

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var workManager: WorkManager

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var pixelSender: PirPixelSender

    @Inject
    lateinit var currentTimeProvider: CurrentTimeProvider

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

        workManager.getWorkInfosForUniqueWorkLiveData(TAG_SCHEDULED_SCAN)
            .observe(
                this,
                Observer { workInfoList ->
                    workInfoList.forEach { workInfo ->
                        logcat { "PIR-DEV: Work State: ${workInfo.state}" }
                        logcat { "PIR-DEV: Work State: ${workInfo.runAttemptCount}" }
                    }
                },
            )
    }

    private fun render(results: List<ScanResult>) {
        val allExtracted = results.filterIsInstance<ExtractedProfileResult>()
        val allError = results.filterIsInstance<ErrorResult>()
        val brokersWithRecords = allExtracted.filter {
            it.extractResults.isNotEmpty()
        }
        val totalSitesScanned = allExtracted.size + allError.size
        val brokersWithRecordsCount = brokersWithRecords.size

        val brokersWithNoRecords = allExtracted.filter {
            it.extractResults.isEmpty()
        }.size + allError.size

        val totalRecordCount = brokersWithRecords.sumOf {
            it.extractResults.size
        }

        with(binding) {
            this.statusSitesScanned.text =
                getString(R.string.pirStatsStatusScanned, totalSitesScanned)
            this.statusTotalRecords.text =
                getString(R.string.pirStatsStatusRecords, totalRecordCount)
            this.statusTotalBrokersFound.text =
                getString(R.string.pirStatsStatusBrokerFound, brokersWithRecordsCount)
            this.statusTotalAllClear.text =
                getString(R.string.pirStatsStatusAllClear, brokersWithNoRecords)
        }
    }

    private fun setupViews() {
        binding.debugRunScan.setOnClickListener {
            notificationManagerCompat.cancel(NOTIF_ID_STATUS_COMPLETE)
            logcat { "PIR-SCAN: Attempting to start PirForegroundScanService from ${Process.myPid()}" }
            lifecycleScope.launch {
                if (useUserInput()) {
                    repository.replaceUserProfile(
                        UserProfile(
                            userName = UserName(
                                firstName = binding.profileFirstName.text,
                                middleName = binding.profileMiddleName.text.ifBlank {
                                    null
                                },
                                lastName = binding.profileLastName.text,
                            ),
                            addresses = Address(
                                city = binding.profileCity.text,
                                state = binding.profileState.text,
                            ),
                            birthYear = binding.profileBirthYear.text.toInt(),
                            age = LocalDate.now().year - binding.profileBirthYear.text.toInt(),
                        ),
                    )
                }
            }
            startForegroundService(Intent(this, PirForegroundScanService::class.java))
            globalActivityStarter.start(this, PirResultsScreenNoParams)
        }

        binding.debugForceKill.setOnClickListener {
            stopService(Intent(this, PirForegroundScanService::class.java))
            lifecycleScope.launch(dispatcherProvider.io()) {
                repository.deleteAllResults()
                repository.deleteAllUserProfiles()
                repository.deleteAllScanLogs()
            }
            notificationManagerCompat.cancel(NOTIF_ID_STATUS_COMPLETE)
            workManager.cancelUniqueWork(TAG_SCHEDULED_SCAN)
            stopService(Intent(this, PirRemoteWorkerService::class.java))
        }

        binding.viewResults.setOnClickListener {
            globalActivityStarter.start(this, PirResultsScreenNoParams)
        }

        binding.scheduleScan.setOnClickListener {
            schedulePeriodicScan()
            Toast.makeText(this, getString(R.string.pirMessageSchedule), Toast.LENGTH_SHORT).show()
        }

        binding.viewScanResults.setOnClickListener {
            globalActivityStarter.start(this, PirScanResultsScreenNoParams)
        }
    }

    private fun schedulePeriodicScan() {
        logcat { "PIR-SCHEDULED: Scheduling periodic scan appId: ${appBuildConfig.applicationId}" }

        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest =
            PeriodicWorkRequest.Builder(PirScheduledScanRemoteWorker::class.java, 12, TimeUnit.HOURS)
                .boundToPirProcess(appBuildConfig.applicationId)
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()

        pixelSender.reportScheduledScanScheduled()
        lifecycleScope.launch {
            repository.saveScanLog(
                PirScanLog(
                    eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    eventType = SCHEDULED_SCAN_SCHEDULED,
                ),
            )
        }

        workManager.enqueueUniquePeriodicWork(
            TAG_SCHEDULED_SCAN,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest,
        )
    }

    private fun PeriodicWorkRequest.Builder.boundToPirProcess(applicationId: String): PeriodicWorkRequest.Builder {
        val componentName = ComponentName(applicationId, PirRemoteWorkerService::class.java.name)
        val data = Data.Builder()
            .putString(RemoteListenableWorker.ARGUMENT_PACKAGE_NAME, componentName.packageName)
            .putString(RemoteListenableWorker.ARGUMENT_CLASS_NAME, componentName.className)
            .build()

        return this.setInputData(data)
    }

    private fun useUserInput(): Boolean {
        return binding.profileFirstName.text.isNotBlank() &&
            binding.profileLastName.text.isNotBlank() &&
            binding.profileCity.text.isNotBlank() &&
            binding.profileState.text.isNotBlank() &&
            binding.profileBirthYear.text.isNotBlank()
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
