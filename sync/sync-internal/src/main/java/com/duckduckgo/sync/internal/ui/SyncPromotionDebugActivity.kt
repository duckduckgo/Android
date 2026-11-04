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

package com.duckduckgo.sync.internal.ui

import android.os.Bundle
import android.text.format.DateFormat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.internal.R
import com.duckduckgo.sync.internal.databinding.ActivitySyncPromotionDebugBinding
import com.duckduckgo.sync.internal.ui.SyncPromotionDebugViewModel.Command
import com.duckduckgo.sync.internal.ui.SyncPromotionDebugViewModel.Command.ShowMessage
import com.duckduckgo.sync.internal.ui.SyncPromotionDebugViewModel.ViewState
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class SyncPromotionDebugActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivitySyncPromotionDebugBinding by viewBinding()
    private val viewModel: SyncPromotionDebugViewModel by bindViewModel()

    private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

    private var viewState = ViewState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableTransparentEdgeToEdge()
        setContentView(binding.root)
        configureEdgeToEdgeInsets()
        setupToolbar(binding.includeToolbar.toolbar)
        configureListeners()
        observeViewState()
        observeCommands()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyScrollableNavigationBarInsets(binding.contentScrollView)
    }

    private fun configureListeners() {
        binding.promotionSetting.setOnClickListener { showPromotionDialog() }
        binding.changePromotionButton.setOnClickListener { showPromotionDialog() }
        binding.clearImpressionsButton.setOnClickListener { viewModel.clearImpressions() }
        binding.changeLastImpressionButton.setOnClickListener {
            pickTime(title = "Last impression", initialTime = viewState.lastImpressionTime, onPicked = viewModel::changeLastImpressionTime)
        }
        binding.clearDismissButton.setOnClickListener { viewModel.clearDismissal() }
        binding.changeDismissButton.setOnClickListener {
            pickTime(title = "Dismissed", initialTime = viewState.dismissTime, onPicked = viewModel::changeDismissTime)
        }
        binding.clearPromotionDataButton.setOnClickListener { viewModel.clearPromotionData() }
        binding.changeInstallationTimeButton.setOnClickListener {
            pickTime(title = "Installation date", initialTime = viewState.installationTime, onPicked = viewModel::changeInstallationTime)
        }
        binding.resetInstallationTimeButton.setOnClickListener { viewModel.resetInstallationTime() }
        binding.clearAllPromotionDataButton.setOnClickListener { viewModel.clearAllPromotionData() }
    }

    private fun observeViewState() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { render(it) }
            .launchIn(lifecycleScope)
    }

    private fun observeCommands() {
        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun render(state: ViewState) {
        viewState = state
        binding.promotionSetting.setSecondaryText(viewModel.labelFor(state.selectedPromotion))
        binding.lastImpressionSetting.setSecondaryText(state.lastImpressionTime?.let(dateTimeFormatter::format) ?: "Never")
        binding.dismissSetting.setSecondaryText(state.dismissTime?.let(dateTimeFormatter::format) ?: "Never")
        val installationTime = state.installationTime?.let(dateTimeFormatter::format) ?: "Unknown"
        binding.installationTimeSetting.setSecondaryText(
            if (state.isInstallationTimeOverridden) "$installationTime (override)" else installationTime,
        )
    }

    private fun processCommand(command: Command) {
        when (command) {
            is ShowMessage -> Snackbar.make(binding.root, command.message, Snackbar.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION") // Options don't need to be localized in the debug screen
    private fun showPromotionDialog() {
        val promotionTypes = viewModel.promotionTypes
        RadioListAlertDialogBuilder(this)
            .setTitle("Selected promo")
            .setOptions(
                promotionTypes.map(viewModel::labelFor),
                promotionTypes.indexOf(viewState.selectedPromotion) + 1,
            )
            .setPositiveButton(com.duckduckgo.mobile.android.R.string.dialogSave)
            .setNegativeButton(android.R.string.cancel)
            .addEventListener(
                object : RadioListAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked(selectedItem: Int) {
                        viewModel.selectPromotion(promotionTypes[selectedItem - 1])
                    }
                },
            )
            .setCancelable(true)
            .show()
    }

    private fun pickTime(title: String, initialTime: Instant?, onPicked: (Instant) -> Unit) {
        val zone = ZoneId.systemDefault()
        val initialDateTime = (initialTime ?: Instant.now()).atZone(zone)

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTheme(R.style.ThemeOverlay_SyncInternal_MaterialCalendar)
            .setTitleText(title)
            .setSelection(initialDateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .setCalendarConstraints(CalendarConstraints.Builder().setValidator(DateValidatorPointBackward.now()).build())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val date = Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC).toLocalDate()
            val timePicker = MaterialTimePicker.Builder()
                .setTheme(R.style.ThemeOverlay_SyncInternal_MaterialTimePicker)
                .setTitleText(title)
                .setTimeFormat(if (DateFormat.is24HourFormat(this)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(initialDateTime.hour)
                .setMinute(initialDateTime.minute)
                .build()
            timePicker.addOnPositiveButtonClickListener {
                onPicked(date.atTime(timePicker.hour, timePicker.minute).atZone(zone).toInstant())
            }
            timePicker.show(supportFragmentManager, TIME_PICKER_TAG)
        }
        datePicker.show(supportFragmentManager, DATE_PICKER_TAG)
    }

    private companion object {
        const val DATE_PICKER_TAG = "SyncPromotionDebugDatePicker"
        const val TIME_PICKER_TAG = "SyncPromotionDebugTimePicker"
    }
}
