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

package com.duckduckgo.agesignals.impl.dev

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.agesignals.impl.AgeSignalsChecker
import com.duckduckgo.agesignals.impl.AgeSignalsScenario
import com.duckduckgo.agesignals.impl.AgeSignalsScenarioStore
import com.duckduckgo.agesignals.impl.AgeSignalsStore
import com.duckduckgo.agesignals.impl.R
import com.duckduckgo.agesignals.impl.databinding.ActivityAgeSignalsDevSettingsBinding
import com.duckduckgo.codegen.annotations.ContributeToActivityStarter
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.RadioListOption
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(AgeSignalsDevSettingsScreen::class)
class AgeSignalsDevSettingsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var checker: AgeSignalsChecker

    @Inject
    lateinit var store: AgeSignalsStore

    @Inject
    lateinit var scenarioStore: AgeSignalsScenarioStore

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivityAgeSignalsDevSettingsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyScrollableNavigationBarInsets(binding.contentScrollView)
        setupToolbar(binding.includeToolbar.toolbar)

        binding.scenario.setClickListener { lifecycleScope.launch { showScenarioPicker() } }
        binding.runNow.setOnClickListener {
            lifecycleScope.launch {
                binding.runNow.isEnabled = false
                checker.check(this@AgeSignalsDevSettingsActivity)
                render()
                binding.runNow.isEnabled = true
            }
        }
        lifecycleScope.launch { render() }
    }

    private suspend fun render() {
        val never = getString(R.string.ageSignalsDevNever)
        binding.lastOutcome.setSecondaryText(store.lastOutcome()?.let { "${it.outcome} at ${formatTime(it.outcomeAt)}" } ?: never)
        binding.lastFailure.setSecondaryText(
            store.lastFailure()?.let { "${it.step} / ${it.error} at ${formatTime(it.failureAt)}" } ?: never,
        )
        binding.lastPrompt.setSecondaryText(store.lastPromptShownAt()?.let { formatTime(it) } ?: never)
        binding.scenario.setSecondaryText(getString(scenarioStore.scenario().title))
    }

    private suspend fun showScenarioPicker() {
        val current = scenarioStore.scenario()
        RadioListAlertDialogBuilder(this)
            .setTitle(R.string.ageSignalsDevScenario)
            .setOptions(AgeSignalsScenario.entries.map { RadioListOption(it.title, isSelected = it == current) })
            .setPositiveButton(R.string.ageSignalsDevSelect)
            .setNegativeButton(R.string.ageSignalsDevCancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        lifecycleScope.launch {
                            scenarioStore.setScenario(AgeSignalsScenario.entries[selectedItem - 1])
                            render()
                        }
                    }
                },
            )
            .show()
    }

    private fun formatTime(millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(millis))
}

data object AgeSignalsDevSettingsScreen : GlobalActivityStarter.ActivityParams {
    private fun readResolve(): Any = AgeSignalsDevSettingsScreen
}
