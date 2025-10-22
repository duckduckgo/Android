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

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.pir.impl.notifications.PirNotificationManager
import com.duckduckgo.pir.impl.optout.PirForegroundOptOutService
import com.duckduckgo.pir.impl.optout.PirForegroundOptOutService.Companion.EXTRA_BROKER_TO_OPT_OUT
import com.duckduckgo.pir.impl.store.PirEventsRepository
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.internal.databinding.ActivityPirInternalOptoutBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirDevOptOutScreenNoParams::class)
class PirDevOptOutActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var pirNotificationManager: PirNotificationManager

    @Inject
    lateinit var eventsRepository: PirEventsRepository

    @Inject
    lateinit var repository: PirRepository

    private lateinit var optOutAdapter: ArrayAdapter<String>
    private lateinit var dropDownAdapter: ArrayAdapter<String>
    private val binding: ActivityPirInternalOptoutBinding by viewBinding()
    private val brokerOptions = mutableListOf<String>()
    private var selectedBroker: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)
        setupViews()
        bindViews()
    }

    private fun setupViews() {
        optOutAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        dropDownAdapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item)
        binding.optOutList.adapter = optOutAdapter
        binding.optOut.setOnClickListener {
            if (selectedBroker != null) {
                pirNotificationManager.cancelNotifications()
                Intent(this, PirForegroundOptOutService::class.java).apply {
                    putExtra(EXTRA_BROKER_TO_OPT_OUT, selectedBroker)
                }.also {
                    startForegroundService(it)
                }
            }
        }

        binding.optOutDebug.setOnClickListener {
            pirNotificationManager.cancelNotifications()
            if (selectedBroker != null) {
                globalActivityStarter.start(
                    this,
                    PirDevWebViewResultsScreenParams(listOf(selectedBroker!!)),
                )
            }
        }

        binding.debugForceKill.setOnClickListener {
            stopService(Intent(this, PirForegroundOptOutService::class.java))
            lifecycleScope.launch {
                eventsRepository.deleteAllOptOutData()
                eventsRepository.deleteAllEmailConfirmationsLogs()
            }
            pirNotificationManager.cancelNotifications()
        }

        binding.viewResults.setOnClickListener {
            globalActivityStarter.start(this, PirResultsScreenParams.PirOptOutResultsScreen)
        }

        binding.optOutBrokers.adapter = dropDownAdapter
        dropDownAdapter.addAll(brokerOptions)

        binding.optOutBrokers.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                selectedBroker = brokerOptions[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun bindViews() {
        lifecycleScope.launch {
            repository.getBrokersForOptOut(formOptOutOnly = true).also {
                brokerOptions.addAll(it)
                dropDownAdapter.clear()
                dropDownAdapter.addAll(brokerOptions)
            }
        }
        eventsRepository.getAllSuccessfullySubmittedOptOutFlow()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { optOuts ->
                optOutAdapter.clear()
                optOutAdapter.addAll(
                    optOuts.map {
                        "${it.value} - ${it.key}"
                    },
                )
            }
            .launchIn(lifecycleScope)
    }
}

object PirDevOptOutScreenNoParams : ActivityParams
