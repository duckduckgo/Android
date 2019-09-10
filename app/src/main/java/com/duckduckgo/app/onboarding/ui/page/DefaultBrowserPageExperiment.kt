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
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserSystemSettings
import com.duckduckgo.app.global.ViewModelFactory
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.content_onboarding_default_browser.continueButton
import kotlinx.android.synthetic.main.content_onboarding_default_browser.launchSettingsButton
import kotlinx.android.synthetic.main.content_onboarding_default_browser_experiment.*
import kotlinx.android.synthetic.main.content_onboarding_default_browser_experiment.defaultBrowserImage
import timber.log.Timber
import javax.inject.Inject
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.SpannableString
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import kotlinx.android.synthetic.main.content_onboarding_default_browser_card.*

class DefaultBrowserPageExperiment : OnboardingPageFragment() {
    override fun layoutResource(): Int = R.layout.content_onboarding_default_browser_experiment

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private var userTriedToSetDDGAsDefault = false
    private var userSelectedExternalBrowser = false
    private var toast: Toast? = null

    private val viewModel: DefaultBrowserPageExperimentViewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(DefaultBrowserPageExperimentViewModel::class.java)
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

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
        viewModel.viewState.observe(this, Observer<DefaultBrowserPageExperimentViewModel.ViewState> { viewState ->
            viewState?.let {
                if (it.showSettingsUI) setUIForSettings() else setUIForDialog()
                if (it.showInstructionsCard) showCard() else hideCard()
                setOnlyContinue(it.showOnlyContinue)
            }
        })

        viewModel.command.observe(this, Observer {
            when (it) {
                is DefaultBrowserPageExperimentViewModel.Command.OpenDialog -> onLaunchDefaultBrowserWithDialogClicked(it.timesOpened, it.url)
                is DefaultBrowserPageExperimentViewModel.Command.OpenSettings -> onLaunchDefaultBrowserSettingsClicked()
                is DefaultBrowserPageExperimentViewModel.Command.ContinueToBrowser -> onContinuePressed()
            }
        })
    }

    private fun setButtonsBehaviour() {
        launchSettingsButton.setOnClickListener {
            viewModel.onDefaultBrowserClicked()
        }
        continueButton.setOnClickListener {
            viewModel.onContinueToBrowser(userTriedToSetDDGAsDefault)
        }
    }

    private fun setOnlyContinue(visible: Boolean) {
        if (visible) {
            continueButton.visibility = View.INVISIBLE
            browserProtectionSubtitle.setText(R.string.defaultBrowserDescriptionDefaultSet)
            browserProtectionTitle.setText(R.string.onboardingDefaultBrowserTitleDefaultSet)

            defaultBrowserImage.setImageResource(R.drawable.hiker)

            extractContinueButtonTextResourceId()?.let { launchSettingsButton.setText(it) }
            launchSettingsButton.setOnClickListener {
                viewModel.onContinueToBrowser(userTriedToSetDDGAsDefault)
            }
        } else {
            launchSettingsButton.setText(R.string.defaultBrowserLetsDoIt)
            continueButton.visibility = View.VISIBLE
            setButtonsBehaviour()
        }
    }

    private fun setUIForDialog() {
        defaultBrowserImage.setImageResource(R.drawable.set_as_default_browser_illustration_experiment)
        browserProtectionSubtitle.setText(R.string.defaultBrowserDescriptionNoDefault)
    }

    private fun setUIForSettings() {
        defaultBrowserImage.setImageResource(R.drawable.set_as_default_browser_illustration)
        browserProtectionSubtitle.setText(R.string.onboardingDefaultBrowserDescription)
    }

    @SuppressLint("InflateParams")
    private fun showCard() {
        toast?.cancel()
        defaultCard.visibility = View.VISIBLE
        defaultCard.alpha = 1f

        val inflater = LayoutInflater.from(requireContext())
        val inflatedView = inflater.inflate(R.layout.content_onboarding_default_browser_card, null)
        val instructionsTextToast = inflatedView.findViewById<TextView>(R.id.instructions)
        val spannableString = getInstructionsCardSpannableString()

        instructionsTextToast.text = spannableString
        instructions.text = spannableString

        toast = Toast(requireContext()).apply {
            view = inflatedView
            setGravity(Gravity.TOP or Gravity.FILL_HORIZONTAL, 0, 0)
            duration = Toast.LENGTH_LONG
        }
        toast?.show()
    }

    private fun getInstructionsCardSpannableString(): SpannableString {
        val instructionsString = getString(R.string.defaultBrowserInstructions)
        val spannableString = SpannableString(instructionsString)
        val instructionsArray = instructionsString.split(ALWAYS.toRegex())

        spannableString.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.cornflowerBlue)),
            instructionsArray[0].length,
            instructionsString.indexOf(instructionsArray[1]),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannableString
    }

    private fun hideCard() {
        toast?.cancel()
        defaultCard.animate()
            .alpha(0f)
            .setDuration(100)
            .start()
    }

    private fun onLaunchDefaultBrowserWithDialogClicked(timesOpened: Int, url: String) {
        userTriedToSetDDGAsDefault = true
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.putExtra(TIMES_OPENED, timesOpened)
        startActivityForResult(intent, DEFAULT_BROWSER_REQUEST_CODE_DIALOG)
    }

    private fun onLaunchDefaultBrowserSettingsClicked() {
        userTriedToSetDDGAsDefault = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val intent = DefaultBrowserSystemSettings.intent()
            try {
                startActivityForResult(intent, DEFAULT_BROWSER_REQUEST_CODE_SETTINGS)
            } catch (e: ActivityNotFoundException) {
                Timber.w(e, getString(R.string.cannotLaunchDefaultAppSettings))
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            DEFAULT_BROWSER_REQUEST_CODE_SETTINGS -> {
                viewModel.handleResult(DefaultBrowserPageExperimentViewModel.Origin.Settings)
            }
            DEFAULT_BROWSER_REQUEST_CODE_DIALOG -> {
                val timesOpened = data?.getIntExtra(TIMES_OPENED, -1)
                val origin =
                    if (timesOpened != null && timesOpened != -1) {
                        DefaultBrowserPageExperimentViewModel.Origin.InternalBrowser(timesOpened)
                    } else {
                        if (userSelectedExternalBrowser) {
                            DefaultBrowserPageExperimentViewModel.Origin.ExternalBrowser
                        } else {
                            DefaultBrowserPageExperimentViewModel.Origin.DialogDismissed
                        }
                    }
                userSelectedExternalBrowser = false
                viewModel.handleResult(origin)
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    companion object {
        private const val ALWAYS = "Always"
        private const val DEFAULT_BROWSER_REQUEST_CODE_SETTINGS = 100
        private const val SAVED_STATE_LAUNCHED_DEFAULT = "SAVED_STATE_LAUNCHED_DEFAULT"
        const val DEFAULT_BROWSER_REQUEST_CODE_DIALOG = 101
        const val TIMES_OPENED = "timesOpened"
    }
}