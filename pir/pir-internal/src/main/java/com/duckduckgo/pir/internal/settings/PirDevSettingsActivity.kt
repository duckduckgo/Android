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
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.pir.internal.databinding.ActivityPirInternalSettingsBinding
import com.duckduckgo.pir.internal.settings.PirResultsScreenParams.PirEventsResultsScreen
import com.duckduckgo.pir.internal.store.PirRepository
import com.duckduckgo.pir.internal.store.PitTestingStore
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirSettingsScreenNoParams::class)
class PirDevSettingsActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var repository: PirRepository

    @Inject
    lateinit var testingStore: PitTestingStore

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

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

        binding.testerInfo.setSecondaryText(testingStore.testerId)
        if (testingStore.testerId != null) {
            binding.testerInfo.setLongClickListener {
                copyDataToClipboard()
            }
        }
    }

    private fun copyDataToClipboard() {
        val clipboardManager = getSystemService(ClipboardManager::class.java)

        lifecycleScope.launch(dispatcherProvider.io()) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("", testingStore.testerId))

            withContext(dispatcherProvider.main()) {
                Toast.makeText(this@PirDevSettingsActivity, "Testing ID copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindViews() {
        lifecycleScope.launch {
            binding.pirDebugOptOut.isEnabled = repository.getBrokersForOptOut(true).isNotEmpty()
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
