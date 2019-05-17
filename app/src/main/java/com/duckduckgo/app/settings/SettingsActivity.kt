/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.settings

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.Observer
import com.duckduckgo.app.about.AboutDuckDuckGoActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.feedback.ui.common.FeedbackActivity
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.sendThemeChangedBroadcast
import com.duckduckgo.app.global.view.launchDefaultAppActivity
import com.duckduckgo.app.settings.SettingsViewModel.AutomaticallyClearData
import com.duckduckgo.app.settings.SettingsViewModel.Command
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import kotlinx.android.synthetic.main.content_settings_general.*
import kotlinx.android.synthetic.main.content_settings_other.*
import kotlinx.android.synthetic.main.content_settings_privacy.*
import kotlinx.android.synthetic.main.include_toolbar.*
import javax.inject.Inject

class SettingsActivity : DuckDuckGoActivity(), SettingsAutomaticallyClearWhatFragment.Listener, SettingsAutomaticallyClearWhenFragment.Listener {

    @Inject
    lateinit var pixel: Pixel

    private val viewModel: SettingsViewModel by bindViewModel()

    private val defaultBrowserChangeListener = OnCheckedChangeListener { _, _ -> launchDefaultAppScreen() }

    private val lightThemeToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onLightThemeToggled(isChecked)
    }

    private val autocompleteToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onAutocompleteSettingChanged(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupActionBar()

        configureUiEventHandlers()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    private fun configureUiEventHandlers() {
        about.setOnClickListener { startActivity(AboutDuckDuckGoActivity.intent(this)) }
        provideFeedback.setOnClickListener { viewModel.userRequestedToSendFeedback() }

        lightThemeToggle.setOnCheckedChangeListener(lightThemeToggleListener)
        autocompleteToggle.setOnCheckedChangeListener(autocompleteToggleListener)
        setAsDefaultBrowserSetting.setOnCheckedChangeListener(defaultBrowserChangeListener)
        automaticallyClearWhatSetting.setOnClickListener { launchAutomaticallyClearWhatDialog() }
        automaticallyClearWhenSetting.setOnClickListener { launchAutomaticallyClearWhenDialog() }
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(this, Observer<SettingsViewModel.ViewState> { viewState ->
            viewState?.let {
                version.setSubtitle(it.version)
                lightThemeToggle.quietlySetIsChecked(it.lightThemeEnabled, lightThemeToggleListener)
                autocompleteToggle.quietlySetIsChecked(it.autoCompleteSuggestionsEnabled, autocompleteToggleListener)
                updateDefaultBrowserViewVisibility(it)
                updateAutomaticClearDataOptions(it.automaticallyClearData)
            }
        })

        viewModel.command.observe(this, Observer {
            processCommand(it)
        })
    }

    private fun updateAutomaticClearDataOptions(automaticallyClearData: AutomaticallyClearData) {
        val clearWhatSubtitle = getString(automaticallyClearData.clearWhatOption.nameStringResourceId())
        automaticallyClearWhatSetting.setSubtitle(clearWhatSubtitle)

        val clearWhenSubtitle = getString(automaticallyClearData.clearWhenOption.nameStringResourceId())
        automaticallyClearWhenSetting.setSubtitle(clearWhenSubtitle)

        val whenOptionEnabled = automaticallyClearData.clearWhenOptionEnabled
        automaticallyClearWhenSetting.isEnabled = whenOptionEnabled
    }

    private fun launchAutomaticallyClearWhatDialog() {
        val dialog = SettingsAutomaticallyClearWhatFragment.create(viewModel.viewState.value?.automaticallyClearData?.clearWhatOption)
        dialog.show(supportFragmentManager, CLEAR_WHAT_DIALOG_TAG)
        pixel.fire(PixelName.AUTOMATIC_CLEAR_DATA_WHAT_SHOWN)
    }

    private fun launchAutomaticallyClearWhenDialog() {
        val dialog = SettingsAutomaticallyClearWhenFragment.create(viewModel.viewState.value?.automaticallyClearData?.clearWhenOption)
        dialog.show(supportFragmentManager, CLEAR_WHEN_DIALOG_TAG)
        pixel.fire(PixelName.AUTOMATIC_CLEAR_DATA_WHEN_SHOWN)
    }

    private fun processCommand(it: Command?) {
        when (it) {
            is Command.LaunchFeedback -> launchFeedback()
            is Command.UpdateTheme -> sendThemeChangedBroadcast()
        }
    }

    private fun updateDefaultBrowserViewVisibility(it: SettingsViewModel.ViewState) {
        if (it.showDefaultBrowserSetting) {
            setAsDefaultBrowserSetting.quietlySetIsChecked(it.isAppDefaultBrowser, defaultBrowserChangeListener)
            setAsDefaultBrowserSetting.visibility = View.VISIBLE
        } else {
            setAsDefaultBrowserSetting.visibility = View.GONE
        }
    }

    private fun launchDefaultAppScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            launchDefaultAppActivity()
        } else {
            throw IllegalStateException("Unable to launch default app activity on this OS")
        }
    }

    private fun setupActionBar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun launchFeedback() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivityForResult(Intent(FeedbackActivity.intent(this)), FEEDBACK_REQUEST_CODE, options)
    }

    override fun onAutomaticallyClearWhatOptionSelected(clearWhatSetting: ClearWhatOption) {
        viewModel.onAutomaticallyWhatOptionSelected(clearWhatSetting)
    }

    override fun onAutomaticallyClearWhenOptionSelected(clearWhenSetting: ClearWhenOption) {
        viewModel.onAutomaticallyWhenOptionSelected(clearWhenSetting)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FEEDBACK_REQUEST_CODE) {
            handleFeedbackResult(resultCode)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleFeedbackResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, R.string.thanksForTheFeedback, Toast.LENGTH_LONG).show()
        }
    }

    @StringRes
    private fun ClearWhatOption.nameStringResourceId(): Int {
        return when (this) {
            ClearWhatOption.CLEAR_NONE -> R.string.settingsAutomaticallyClearWhatOptionNone
            ClearWhatOption.CLEAR_TABS_ONLY -> R.string.settingsAutomaticallyClearWhatOptionTabs
            ClearWhatOption.CLEAR_TABS_AND_DATA -> R.string.settingsAutomaticallyClearWhatOptionTabsAndData
        }
    }

    @StringRes
    private fun ClearWhenOption.nameStringResourceId(): Int {
        return when (this) {
            ClearWhenOption.APP_EXIT_ONLY -> R.string.settingsAutomaticallyClearWhenAppExitOnly
            ClearWhenOption.APP_EXIT_OR_5_MINS -> R.string.settingsAutomaticallyClearWhenAppExit5Minutes
            ClearWhenOption.APP_EXIT_OR_15_MINS -> R.string.settingsAutomaticallyClearWhenAppExit15Minutes
            ClearWhenOption.APP_EXIT_OR_30_MINS -> R.string.settingsAutomaticallyClearWhenAppExit30Minutes
            ClearWhenOption.APP_EXIT_OR_60_MINS -> R.string.settingsAutomaticallyClearWhenAppExit60Minutes
            ClearWhenOption.APP_EXIT_OR_5_SECONDS -> R.string.settingsAutomaticallyClearWhenAppExit5Seconds
        }
    }

    companion object {
        private const val CLEAR_WHAT_DIALOG_TAG = "CLEAR_WHAT_DIALOG_FRAGMENT"
        private const val CLEAR_WHEN_DIALOG_TAG = "CLEAR_WHEN_DIALOG_FRAGMENT"
        private const val FEEDBACK_REQUEST_CODE = 100

        fun intent(context: Context): Intent {
            return Intent(context, SettingsActivity::class.java)
        }
    }
}

/**
 * Utility method to toggle a switch without broadcasting to its change listener
 *
 * This is useful for when setting the checked state from the view model where we want the switch state to match some value, but this act itself
 * should not result in the checked change event handler being fired
 *
 * Requires the change listener to be provided explicitly as it is held privately in the super class and cannot be accessed automatically.
 */
private fun SwitchCompat.quietlySetIsChecked(newCheckedState: Boolean, changeListener: OnCheckedChangeListener?) {
    setOnCheckedChangeListener(null)
    isChecked = newCheckedState
    setOnCheckedChangeListener(changeListener)
}
