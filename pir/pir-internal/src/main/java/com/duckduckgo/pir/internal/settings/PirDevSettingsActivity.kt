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
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.pir.internal.checker.PirWorkHandler
import com.duckduckgo.pir.internal.databinding.ActivityPirInternalSettingsBinding
import com.duckduckgo.pir.internal.settings.PirResultsScreenParams.PirEventsResultsScreen
import com.duckduckgo.pir.internal.store.PirRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirSettingsScreenNoParams::class)
class PirDevSettingsActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var repository: PirRepository

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var pirWorkHandler: PirWorkHandler

    private val binding: ActivityPirInternalSettingsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)
        setupViews()
        createNotificationChannel()
        bindViews()
    }

    private fun setupViews() {
        binding.pirDebugScan.setOnClickListener {
            globalActivityStarter.start(this, PirDevScanScreenNoParams)
        }

        binding.pirDebugOptOut.setOnClickListener {
            globalActivityStarter.start(this, PirDevOptOutScreenNoParams)
        }

        binding.viewRunEvents.setOnClickListener {
            globalActivityStarter.start(this, PirEventsResultsScreen)
        }

        binding.cancelAllWork.setOnClickListener {
            pirWorkHandler.cancelWork()
        }
    }

    private fun bindViews() {
        lifecycleScope.launch {
            val canRunPir = pirWorkHandler.canRunPir().firstOrNull() == true
            binding.pirDebugScan.isEnabled = canRunPir
            binding.pirDebugOptOut.isEnabled = canRunPir && repository.getBrokersForOptOut(true).isNotEmpty()
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
