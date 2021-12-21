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
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.ViewCompat.requestApplyInsets
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserSystemSettings
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.mobile.android.ui.view.show
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.android.synthetic.main.content_onboarding_default_browser.*
import kotlinx.android.synthetic.main.include_default_browser_buttons.*
import timber.log.Timber

class DefaultBrowserPage : OnboardingPageFragment() {

    @Inject lateinit var viewModelFactory: ViewModelFactory

    @Inject lateinit var variantManager: VariantManager

    private var userTriedToSetDDGAsDefault = false
    private var userSelectedExternalBrowser = false
    private var toast: Toast? = null
    private var defaultCard: View? = null

    private val viewModel: DefaultBrowserPageViewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(DefaultBrowserPageViewModel::class.java)
    }

    override fun layoutResource(): Int = R.layout.content_onboarding_default_browser

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser) {
            applyStyle()
            viewModel.pageBecameVisible()
        }
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

    private fun applyStyle() {
        activity?.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            statusBarColor = Color.WHITE
        }
        requestApplyInsets(longDescriptionContainer)
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(
            viewLifecycleOwner,
            Observer { viewState ->
                viewState?.let {
                    when (it) {
                        is DefaultBrowserPageViewModel.ViewState.DefaultBrowserSettingsUI -> {
                            setUiForSettings()
                            hideInstructionsCard()
                        }
                        is DefaultBrowserPageViewModel.ViewState.DefaultBrowserDialogUI -> {
                            setUiForDialog()
                            if (it.showInstructionsCard) showInstructionsCard()
                            else hideInstructionsCard()
                        }
                        is DefaultBrowserPageViewModel.ViewState.ContinueToBrowser -> {
                            hideInstructionsCard()
                            onContinuePressed()
                        }
                    }
                }
            })

        viewModel.command.observe(
            this,
            Observer {
                when (it) {
                    is DefaultBrowserPageViewModel.Command.OpenDialog ->
                        onLaunchDefaultBrowserWithDialogClicked(it.url)
                    is DefaultBrowserPageViewModel.Command.OpenSettings ->
                        onLaunchDefaultBrowserSettingsClicked()
                    is DefaultBrowserPageViewModel.Command.ContinueToBrowser -> {
                        hideInstructionsCard()
                        onContinuePressed()
                    }
                }
            })
    }

    private fun setUiForDialog() {
        defaultBrowserImage.setImageResource(R.drawable.set_as_default_browser_illustration_dialog)
        browserProtectionSubtitle.setText(R.string.defaultBrowserDescriptionNoDefault)
        browserProtectionTitle.setText(R.string.onboardingDefaultBrowserTitle)
        launchSettingsButton.setText(R.string.defaultBrowserLetsDoIt)
        setButtonsBehaviour()
    }

    private fun setUiForSettings() {
        defaultBrowserImage.setImageResource(
            R.drawable.set_as_default_browser_illustration_settings)
        browserProtectionSubtitle.setText(R.string.onboardingDefaultBrowserDescription)
        browserProtectionTitle.setText(R.string.onboardingDefaultBrowserTitle)
        launchSettingsButton.setText(R.string.defaultBrowserLetsDoIt)
        setButtonsBehaviour()
    }

    private fun setButtonsBehaviour() {
        launchSettingsButton.setOnClickListener { viewModel.onDefaultBrowserClicked() }
        continueButton.setOnClickListener {
            viewModel.onContinueToBrowser(userTriedToSetDDGAsDefault)
        }
    }

    @SuppressLint("InflateParams")
    private fun showInstructionsCard() {
        toast?.cancel()
        defaultCard?.show()
        defaultCard?.alpha = 1f

        val inflater = LayoutInflater.from(requireContext())
        val inflatedView = inflater.inflate(R.layout.content_onboarding_default_browser_card, null)

        toast =
            Toast(requireContext()).apply {
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
