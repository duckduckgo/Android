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
import com.duckduckgo.pir.internal.databinding.ActivityPirInternalResultsBinding
import com.duckduckgo.pir.internal.store.PirRepository
import com.duckduckgo.pir.internal.store.PirRepository.ScanResult
import com.duckduckgo.pir.internal.store.PirRepository.ScanResult.ErrorResult
import com.duckduckgo.pir.internal.store.PirRepository.ScanResult.ExtractedProfileResult
import com.duckduckgo.pir.internal.store.PirRepository.ScanResult.NavigateResult
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirResultsScreenNoParams::class)
class PirResultsActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var repository: PirRepository

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private val binding: ActivityPirInternalResultsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)
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
                        stringBuilder.append("> ${extract.scrapedData.profileUrl} \n")
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
}

object PirResultsScreenNoParams : ActivityParams
