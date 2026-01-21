/*
 * Copyright (c) 2026 DuckDuckGo
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
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.pir.impl.brokers.BrokerJsonUpdater
import com.duckduckgo.pir.impl.brokers.StepsAsStringAdapter
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.pir.impl.service.DbpService.PirJsonBroker
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.internal.R
import com.duckduckgo.pir.internal.databinding.ActivityPirInternalBrokerConfigBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirBrokerConfigScreenNoParams::class)
class PirDevBrokerConfigActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var repository: PirRepository

    @Inject
    lateinit var brokerJsonUpdater: BrokerJsonUpdater

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private val binding: ActivityPirInternalBrokerConfigBinding by viewBinding()
    private lateinit var dropDownAdapter: ArrayAdapter<String>
    private val brokerOptions = mutableListOf<Broker>()
    private var selectedBroker: Broker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)
        setupViews()
        loadBrokers()
    }

    private fun setupViews() {
        dropDownAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item)
        dropDownAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.brokerSpinner.adapter = dropDownAdapter

        binding.brokerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                selectedBroker = brokerOptions[position]
                binding.statusText.isVisible = false
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedBroker = null
            }
        }

        binding.updateConfigButton.setOnClickListener {
            updateBrokerConfig()
        }

        binding.resetAllConfigsButton.setOnClickListener {
            resetAllBrokerConfigs()
        }
    }

    private fun loadBrokers() {
        lifecycleScope.launch {
            val brokers = repository.getAllActiveBrokerObjects()
            brokerOptions.clear()
            brokerOptions.addAll(brokers)
            dropDownAdapter.clear()
            dropDownAdapter.addAll(brokers.map { it.name })
        }
    }

    private fun updateBrokerConfig() {
        val broker = selectedBroker
        if (broker == null) {
            showError(getString(R.string.pirDevBrokerConfigNoBrokerSelected))
            return
        }

        val jsonInput = binding.brokerJsonInput.text.trim()
        if (jsonInput.isEmpty()) {
            showError(getString(R.string.pirDevBrokerConfigEmptyJson))
            return
        }

        lifecycleScope.launch {
            binding.updateConfigButton.isEnabled = false
            binding.statusText.isVisible = false

            try {
                val parsedBroker = withContext(dispatcherProvider.io()) {
                    parseJsonToBroker(jsonInput)
                }

                if (parsedBroker != null) {
                    withContext(dispatcherProvider.io()) {
                        repository.updateBrokerData(broker.fileName, parsedBroker)
                        repository.setHasBrokerConfigBeenManuallyUpdated(true)
                    }
                    logcat { "PIR-BROKER-CONFIG: Successfully updated broker config for ${broker.name}" }
                    Toast.makeText(
                        this@PirDevBrokerConfigActivity,
                        getString(R.string.pirDevBrokerConfigUpdateSuccess, broker.name),
                        Toast.LENGTH_SHORT,
                    ).show()
                    binding.statusText.isVisible = false
                } else {
                    showError(getString(R.string.pirDevBrokerConfigParseError))
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "PIR-BROKER-CONFIG: Failed to update broker config: ${e.message}" }
                showError(getString(R.string.pirDevBrokerConfigUpdateError, e.message ?: "Unknown error"))
            } finally {
                binding.updateConfigButton.isEnabled = true
            }
        }
    }

    private fun parseJsonToBroker(json: String): PirJsonBroker? {
        return try {
            val adapter = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .add(StepsAsStringAdapter())
                .build()
                .adapter(PirJsonBroker::class.java)

            adapter.fromJson(json)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "PIR-BROKER-CONFIG: JSON parse error: ${e.message}" }
            null
        }
    }

    private fun resetAllBrokerConfigs() {
        lifecycleScope.launch {
            binding.resetAllConfigsButton.isEnabled = false
            binding.updateConfigButton.isEnabled = false
            binding.statusText.isVisible = false

            try {
                withContext(dispatcherProvider.io()) {
                    // Clear etags to force re-download
                    repository.updateMainEtag(null)
                    repository.clearAllBrokerJsons()
                    repository.setHasBrokerConfigBeenManuallyUpdated(false)
                }

                logcat { "PIR-BROKER-CONFIG: Starting broker config re-download..." }

                val success = withContext(dispatcherProvider.io()) {
                    brokerJsonUpdater.update()
                }

                if (success) {
                    logcat { "PIR-BROKER-CONFIG: Successfully reset and re-downloaded all broker configs" }
                    Toast.makeText(
                        this@PirDevBrokerConfigActivity,
                        getString(R.string.pirDevBrokerConfigResetSuccess),
                        Toast.LENGTH_SHORT,
                    ).show()
                    loadBrokers()
                } else {
                    showError(getString(R.string.pirDevBrokerConfigResetError))
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "PIR-BROKER-CONFIG: Failed to reset broker configs: ${e.message}" }
                showError(getString(R.string.pirDevBrokerConfigResetError))
            } finally {
                binding.resetAllConfigsButton.isEnabled = true
                binding.updateConfigButton.isEnabled = true
            }
        }
    }

    private fun showError(message: String) {
        binding.statusText.text = message
        binding.statusText.isVisible = true
    }
}

object PirBrokerConfigScreenNoParams : ActivityParams
