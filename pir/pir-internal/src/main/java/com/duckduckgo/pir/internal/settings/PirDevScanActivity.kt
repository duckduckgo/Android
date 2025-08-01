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

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
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
import com.duckduckgo.pir.internal.databinding.ActivityPirInternalScanBinding
import com.duckduckgo.pir.internal.models.ExtractedProfile
import com.duckduckgo.pir.internal.pixels.PirPixelSender
import com.duckduckgo.pir.internal.scan.PirForegroundScanService
import com.duckduckgo.pir.internal.scan.PirRemoteWorkerService
import com.duckduckgo.pir.internal.scan.PirScheduledScanRemoteWorker
import com.duckduckgo.pir.internal.scan.PirScheduledScanRemoteWorker.Companion.TAG_SCHEDULED_SCAN
import com.duckduckgo.pir.internal.settings.PirDevSettingsActivity.Companion.NOTIF_ID_STATUS_COMPLETE
import com.duckduckgo.pir.internal.store.PirRepository
import com.duckduckgo.pir.internal.store.PirSchedulingRepository
import com.duckduckgo.pir.internal.store.db.Address
import com.duckduckgo.pir.internal.store.db.EventType
import com.duckduckgo.pir.internal.store.db.PirEventLog
import com.duckduckgo.pir.internal.store.db.UserName
import com.duckduckgo.pir.internal.store.db.UserProfile
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.logcat

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirDevScanScreenNoParams::class)
class PirDevScanActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var repository: PirRepository

    @Inject
    lateinit var pirSchedulingRepository: PirSchedulingRepository

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

    private val binding: ActivityPirInternalScanBinding by viewBinding()
    private val recordStringBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)
        setupViews()
        bindViews()
    }

    private fun bindViews() {
        repository.getAllExtractedProfilesFlow()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach {
                render(it)
            }
            .launchIn(lifecycleScope)

        repository.getTotalScannedBrokersFlow()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach {
                binding.statusSitesScanned.text = getString(R.string.pirStatsStatusScanned, it)
            }
            .launchIn(lifecycleScope)
    }

    private fun render(extractedProfiles: List<ExtractedProfile>) {
        val totalBrokersWithProfile = extractedProfiles.map { it.brokerName }.distinct()

        with(binding) {
            this.statusTotalRecords.text =
                getString(R.string.pirStatsStatusRecords, extractedProfiles.size)
            this.statusTotalBrokersFound.text =
                getString(R.string.pirStatsStatusBrokerFound, totalBrokersWithProfile.size)
            recordStringBuilder.clear()

            recordStringBuilder.append("\nRecords found:\n")
            extractedProfiles.groupingBy { it.brokerName }.eachCount().forEach {
                recordStringBuilder.append("${it.key} - records: ${it.value}\n")
            }
            this.records.text = recordStringBuilder.toString()
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
                                firstName = binding.profileFirstName.text.trim(),
                                middleName = binding.profileMiddleName.text.trim().ifBlank {
                                    null
                                },
                                lastName = binding.profileLastName.text.trim(),
                            ),
                            addresses = Address(
                                city = binding.profileCity.text.trim(),
                                state = binding.profileState.text.trim(),
                            ),
                            birthYear = binding.profileBirthYear.text.trim().toInt(),
                        ),
                    )
                }
            }
            startForegroundService(Intent(this, PirForegroundScanService::class.java))
            globalActivityStarter.start(this, PirResultsScreenParams.PirScanResultsScreen)
        }

        binding.debugForceKill.setOnClickListener {
            killRunningWork()
        }

        binding.debugResetAll.setOnClickListener {
            killRunningWork()
            lifecycleScope.launch(dispatcherProvider.io()) {
                repository.deleteAllScanResults()
                repository.deleteAllUserProfilesQueries()
                repository.deleteEventLogs()
                repository.deleteAllOptOutData()
                pirSchedulingRepository.deleteAllJobRecords()
            }
        }

        binding.debugResetOptOut.setOnClickListener {
            killRunningWork()
            lifecycleScope.launch(dispatcherProvider.io()) {
                pirSchedulingRepository.deleteAllOptOutJobRecords()
            }
        }

        binding.viewResults.setOnClickListener {
            globalActivityStarter.start(this, PirResultsScreenParams.PirScanResultsScreen)
        }

        binding.scheduleScan.setOnClickListener {
            schedulePeriodicScan()
            Toast.makeText(this, getString(R.string.pirMessageSchedule), Toast.LENGTH_SHORT).show()
        }
    }

    private fun killRunningWork() {
        stopService(Intent(this, PirForegroundScanService::class.java))
        notificationManagerCompat.cancel(NOTIF_ID_STATUS_COMPLETE)
        workManager.cancelUniqueWork(TAG_SCHEDULED_SCAN)
        stopService(Intent(this, PirRemoteWorkerService::class.java))
    }

    private fun schedulePeriodicScan() {
        logcat { "PIR-SCHEDULED: Scheduling periodic scan appId: ${appBuildConfig.applicationId}" }

        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest =
            PeriodicWorkRequest.Builder(
                PirScheduledScanRemoteWorker::class.java,
                12,
                TimeUnit.HOURS,
            )
                .boundToPirProcess(appBuildConfig.applicationId)
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()

        pixelSender.reportScheduledScanScheduled()
        lifecycleScope.launch {
            repository.saveScanLog(
                PirEventLog(
                    eventTimeInMillis = currentTimeProvider.currentTimeMillis(),
                    eventType = EventType.SCHEDULED_SCAN_SCHEDULED,
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
}

object PirDevScanScreenNoParams : ActivityParams
