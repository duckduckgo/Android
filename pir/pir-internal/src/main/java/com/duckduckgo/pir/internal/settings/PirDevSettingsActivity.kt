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
import com.duckduckgo.pir.impl.checker.PirWorkHandler
import com.duckduckgo.pir.impl.dashboard.PirDashboardUrlProvider
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebConstants
import com.duckduckgo.pir.impl.notifications.PirNotificationManager
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.internal.R
import com.duckduckgo.pir.internal.databinding.ActivityPirInternalSettingsBinding
import com.duckduckgo.pir.internal.settings.PirResultsScreenParams.PirEventsResultsScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    @Inject
    lateinit var pirNotificationManager: PirNotificationManager

    @Inject
    lateinit var pirInternalSettingsDataStore: PirInternalSettingsDataStore

    @Inject
    lateinit var pirDashboardUrlProvider: PirDashboardUrlProvider

    private val binding: ActivityPirInternalSettingsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)
        setupViews()
        bindViews()
        pirNotificationManager.createNotificationChannel()
    }

    private fun setupViews() {
        binding.pirDebugScan.setOnClickListener {
            globalActivityStarter.start(this, PirDevScanScreenNoParams)
        }

        binding.pirDebugOptOut.setOnClickListener {
            globalActivityStarter.start(this, PirDevOptOutScreenNoParams)
        }

        binding.pirDebugEmail.setOnClickListener {
            globalActivityStarter.start(this, PirDevEmailScreenNoParams)
        }

        binding.viewRunEvents.setOnClickListener {
            globalActivityStarter.start(this, PirEventsResultsScreen)
        }

        binding.brokerConfig.setOnClickListener {
            globalActivityStarter.start(this, PirBrokerConfigScreenNoParams)
        }

        binding.pirCustomUrlInput.text = pirDashboardUrlProvider.getUrl()

        binding.pirSetCustomUrl.setOnClickListener {
            val url = binding.pirCustomUrlInput.text
            if (url.isBlank()) {
                Toast.makeText(this, getString(R.string.pirDevCustomUrlEmpty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pirInternalSettingsDataStore.customDashboardUrl = url
            Toast.makeText(this, getString(R.string.pirDevCustomUrlSet), Toast.LENGTH_SHORT).show()
        }

        binding.pirResetCustomUrl.setOnClickListener {
            pirInternalSettingsDataStore.customDashboardUrl = null
            binding.pirCustomUrlInput.text = PirDashboardWebConstants.DEFAULT_WEB_UI_URL
            Toast.makeText(this, getString(R.string.pirDevCustomUrlReset), Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindViews() {
        lifecycleScope.launch {
            pirWorkHandler.canRunPir().collectLatest { canRunPir ->
                binding.pirDebugScan.isEnabled = canRunPir
                binding.pirDebugOptOut.isEnabled = canRunPir && repository.getBrokersForOptOut(true).isNotEmpty()
            }
        }
    }
}

object PirSettingsScreenNoParams : ActivityParams
