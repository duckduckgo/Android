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
import android.widget.ArrayAdapter
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
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.internal.R
import com.duckduckgo.pir.internal.databinding.ActivityPirInternalResultsBinding
import com.duckduckgo.pir.internal.settings.PirResultsScreenParams.PirEventsResultsScreen
import com.duckduckgo.pir.internal.settings.PirResultsScreenParams.PirOptOutResultsScreen
import com.duckduckgo.pir.internal.settings.PirResultsScreenParams.PirScanResultsScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirResultsScreenParams::class)
class PirResultsActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var repository: PirRepository

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private val formatter = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
    private val binding: ActivityPirInternalResultsBinding by viewBinding()

    private val params: PirResultsScreenParams?
        get() = intent.getActivityParams(PirResultsScreenParams::class.java)

    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)
        bindViews()
    }

    private fun bindViews() {
        adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        binding.scanLogList.adapter = adapter

        when (params) {
            is PirEventsResultsScreen -> {
                setTitle(R.string.pirDevViewRunEvents)
                showAllEvents()
            }

            is PirScanResultsScreen -> {
                setTitle(R.string.pirDevViewScanResults)
                showScanResults()
            }

            is PirOptOutResultsScreen -> {
                setTitle(R.string.pirDevViewOptOutResults)
                showOptOutResults()
            }

            null -> {}
        }
    }

    private fun showOptOutResults() {
        repository.getAllOptOutActionLogFlow()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { optOutEvents ->
                optOutEvents.map { result ->
                    val stringBuilder = StringBuilder()
                    stringBuilder.append("Time: ${formatter.format(Date(result.completionTimeInMillis))}\n")
                    stringBuilder.append("BROKER NAME: ${result.brokerName}\n")
                    stringBuilder.append("EXTRACTED PROFILE: ${result.extractedProfile}\n")
                    stringBuilder.append("ACTION EXECUTED: ${result.actionType}\n")
                    stringBuilder.append("IS ERROR: ${result.isError}\n")
                    stringBuilder.append("RAW RESULT: ${result.result}\n")
                    stringBuilder.toString()
                }.also {
                    render(it)
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun showScanResults() {
        repository.getScannedBrokersFlow()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { scanResults ->
                scanResults.map {
                    val stringBuilder = StringBuilder()
                    stringBuilder.append("BROKER NAME: ${it.brokerName}\n")
                    stringBuilder.append("PROFILE ID: ${it.profileQueryId}\n")
                    stringBuilder.append("COMPLETED WITH NO ERROR: ${it.isSuccess}\n")
                    stringBuilder.append("DURATION: ${it.endTimeInMillis - it.startTimeInMillis}\n")
                    stringBuilder.toString()
                }.also {
                    render(it)
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun showAllEvents() {
        repository.getAllEventLogsFlow()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { scanEvents ->
                scanEvents.map { result ->
                    "Time: ${formatter.format(Date(result.eventTimeInMillis))}\nEVENT: ${result.eventType}\n"
                }.also {
                    render(it)
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun render(results: List<String>) {
        adapter.clear()
        adapter.addAll(results)
    }
}

sealed class PirResultsScreenParams : ActivityParams {
    data object PirEventsResultsScreen : PirResultsScreenParams()
    data object PirScanResultsScreen : PirResultsScreenParams()
    data object PirOptOutResultsScreen : PirResultsScreenParams()
}
