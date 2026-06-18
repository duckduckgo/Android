/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserSystemSettings
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class DefaultBrowserPage : OnboardingPageFragment(R.layout.content_onboarding_default_browser) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    private var userTriedToSetDDGAsDefault = false
    private var userSelectedExternalBrowser = false
    private var toast: Toast? = null

    private var defaultCard: View? = null
    private lateinit var headerImage: ImageView
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var primaryButton: DaxButton
    private lateinit var secondaryButton: DaxButton

    private val viewModel: DefaultBrowserPageViewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(DefaultBrowserPageViewModel::class.java)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        headerImage = view.findViewById(R.id.defaultBrowserImage)
        title = view.findViewById(R.id.browserProtectionTitle)
        subtitle = view.findViewById(R.id.browserProtectionSubtitle)
        primaryButton = view.findViewById(R.id.launchSettingsButton)
        secondaryButton = view.findViewById(R.id.continueButton)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        defaultCard = activity?.findViewById(R.id.defaultCard)

        if (savedInstanceState != null) {
            userTriedToSetDDGAsDefault = savedInstanceState.getBoolean(SAVED_STATE_LAUNCHED_DEFAULT)
        }

        observeViewModel()

        setButtonsBehaviour()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadUI()
    }

    override fun onStop() {
        super.onStop()
        userSelectedExternalBrowser = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SAVED_STATE_LAUNCHED_DEFAULT, userTriedToSetDDGAsDefault)
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(viewLifecycleOwner) { viewState ->
            viewState?.let {
                when (it) {
                    is DefaultBrowserPageViewModel.ViewState.DefaultBrowserSettingsUI -> {
                        setUiForSettings()
                        hideInstructionsCard()
                    }
                    is DefaultBrowserPageViewModel.ViewState.DefaultBrowserDialogUI -> {
                        setUiForDialog()
                        if (it.showInstructionsCard) showInstructionsCard() else hideInstructionsCard()
                    }
                    is DefaultBrowserPageViewModel.ViewState.ContinueToBrowser -> {
                        hideInstructionsCard()
                        onContinuePressed()
                    }
                }
            }
        }

        viewModel.command.observe(viewLifecycleOwner) {
            when (it) {
                is DefaultBrowserPageViewModel.Command.OpenDialog -> onLaunchDefaultBrowserWithDialogClicked(it.url)
                is DefaultBrowserPageViewModel.Command.OpenSettings -> onLaunchDefaultBrowserSettingsClicked()
                is DefaultBrowserPageViewModel.Command.ContinueToBrowser -> {
                    hideInstructionsCard()
                    onContinuePressed()
                }
            }
        }
    }

    private fun setUiForDialog() {
        headerImage.setImageResource(R.drawable.set_as_default_browser_illustration_dialog)
        subtitle.setText(R.string.defaultBrowserDescriptionNoDefault)
        title.setText(R.string.onboardingDefaultBrowserTitle)
        primaryButton.setText(R.string.setAsDefaultBrowser)
        setButtonsBehaviour()
    }

    private fun setUiForSettings() {
        headerImage.setImageResource(R.drawable.set_as_default_browser_illustration_settings)
        subtitle.setText(R.string.onboardingDefaultBrowserDescription)
        title.setText(R.string.onboardingDefaultBrowserTitle)
        primaryButton.setText(R.string.setAsDefaultBrowser)
        setButtonsBehaviour()
    }

    private fun setButtonsBehaviour() {
        primaryButton.setOnClickListener {
            viewModel.onDefaultBrowserClicked()
        }
        secondaryButton.setOnClickListener {
            viewModel.onContinueToBrowser()
        }
    }

    @SuppressLint("InflateParams")
    private fun showInstructionsCard() {
        toast?.cancel()
        defaultCard?.show()
        defaultCard?.alpha = 1f

        val inflater = LayoutInflater.from(requireContext())
        val inflatedView = inflater.inflate(R.layout.content_onboarding_default_browser_card, null)

        toast = Toast(requireContext()).apply {
            view = inflatedView
            setGravity(Gravity.TOP or Gravity.FILL_HORIZONTAL, 0, 0)
            duration = Toast.LENGTH_LONG
        }
        toast?.show()
    }

    private fun hideInstructionsCard() {
        toast?.cancel()
        defaultCard?.animate()?.alpha(0f)?.setDuration(100)?.start()
    }

    private fun onLaunchDefaultBrowserWithDialogClicked(url: String) {
        userTriedToSetDDGAsDefault = true
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.putExtra(BrowserActivity.LAUNCH_FROM_DEFAULT_BROWSER_DIALOG, true)
        startActivityForResult(intent, DEFAULT_BROWSER_REQUEST_CODE_DIALOG)
    }

    private fun onLaunchDefaultBrowserSettingsClicked() {
        userTriedToSetDDGAsDefault = true
        val intent = DefaultBrowserSystemSettings.intent()
        try {
            startActivityForResult(intent, DEFAULT_BROWSER_REQUEST_CODE_SETTINGS)
        } catch (e: ActivityNotFoundException) {
            logcat(WARN) { "{${getString(R.string.cannotLaunchDefaultAppSettings)}: ${e.asLog()}" }
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        when (requestCode) {
            DEFAULT_BROWSER_REQUEST_CODE_SETTINGS -> {
                viewModel.handleResult(DefaultBrowserPageViewModel.Origin.Settings)
            }
            DEFAULT_BROWSER_REQUEST_CODE_DIALOG -> {
                val origin =
                    if (resultCode == DEFAULT_BROWSER_RESULT_CODE_DIALOG_INTERNAL) {
                        DefaultBrowserPageViewModel.Origin.InternalBrowser
                    } else {
                        if (userSelectedExternalBrowser) {
                            DefaultBrowserPageViewModel.Origin.ExternalBrowser
                        } else {
                            DefaultBrowserPageViewModel.Origin.DialogDismissed
                        }
                    }
                userSelectedExternalBrowser = false
                viewModel.handleResult(origin)
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    companion object {
        private const val DEFAULT_BROWSER_REQUEST_CODE_SETTINGS = 100
        private const val SAVED_STATE_LAUNCHED_DEFAULT = "SAVED_STATE_LAUNCHED_DEFAULT"
        const val DEFAULT_BROWSER_REQUEST_CODE_DIALOG = 101
        const val DEFAULT_BROWSER_RESULT_CODE_DIALOG_INTERNAL = 102
    }
}
