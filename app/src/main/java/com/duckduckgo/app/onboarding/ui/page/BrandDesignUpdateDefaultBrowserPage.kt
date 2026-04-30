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

package com.duckduckgo.app.onboarding.ui.page

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserSystemSettings
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(FragmentScope::class)
class BrandDesignUpdateDefaultBrowserPage :
    OnboardingPageFragment(R.layout.content_onboarding_default_browser_update) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var appTheme: AppTheme

    private var userTriedToSetDDGAsDefault = false
    private var userSelectedExternalBrowser = false
    private var toast: Toast? = null

    private var defaultCard: View? = null
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var primaryButton: DaxButton
    private lateinit var secondaryButton: DaxButton

    private val viewModel: DefaultBrowserPageViewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(DefaultBrowserPageViewModel::class.java)
    }

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        val themeRes = if (appTheme.isLightModeEnabled()) {
            CommonR.style.Theme_DuckDuckGo_Light_Onboarding
        } else {
            CommonR.style.Theme_DuckDuckGo_Dark_Onboarding
        }
        val contextThemeWrapper = ContextThemeWrapper(inflater.context, themeRes)
        return inflater.cloneInContext(contextThemeWrapper)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        title = view.findViewById(R.id.browserProtectionTitle)
        subtitle = view.findViewById(R.id.browserProtectionSubtitle)
        primaryButton = view.findViewById(R.id.launchSettingsButton)
        secondaryButton = view.findViewById(R.id.continueButton)

        adaptHeaderImageToAvailableSpace(view)
    }

    private fun adaptHeaderImageToAvailableSpace(view: View) {
        val image = view.findViewById<ImageView>(R.id.defaultBrowserImage) ?: return
        val scroll = view.findViewById<NestedScrollView>(R.id.contentScroll) ?: return
        val density = resources.displayMetrics.density
        val minImageHeightPx = (HEADER_IMAGE_MIN_DP * density).toInt()
        val designWidthPx = (HEADER_IMAGE_DESIGN_WIDTH_DP * density).toInt()
        scroll.viewTreeObserver.addOnPreDrawListener {
            // On wide screens (tablets) cap the image width to the design width so centerCrop
            // doesn't scale the asset up vertically and clip the top and bottom.
            if (scroll.width > designWidthPx && image.layoutParams.width != designWidthPx) {
                image.updateLayoutParams { width = designWidthPx }
                return@addOnPreDrawListener false
            }
            if (image.layoutParams.height == minImageHeightPx) return@addOnPreDrawListener true
            if (!scroll.canScrollVertically(1)) return@addOnPreDrawListener true
            image.scaleType = ImageView.ScaleType.FIT_CENTER
            image.updateLayoutParams { height = minImageHeightPx }
            false
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

    @Suppress("DEPRECATION")
    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        maybeReportPageShown()
    }

    override fun onResume() {
        super.onResume()
        maybeReportPageShown()
        viewModel.loadUI()
    }

    @Suppress("DEPRECATION")
    private fun maybeReportPageShown() {
        if (userVisibleHint && isResumed && view != null) {
            viewModel.onPageShown()
        }
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
        // Brand-design variant uses a single placeholder illustration for both states; only copy varies.
        subtitle.setText(R.string.defaultBrowserDescriptionNoDefault)
        title.setText(R.string.onboardingDefaultBrowserTitle)
        primaryButton.setText(R.string.setAsDefaultBrowser)
        setButtonsBehaviour()
    }

    private fun setUiForSettings() {
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
        startActivityForResult(intent, DefaultBrowserPage.DEFAULT_BROWSER_REQUEST_CODE_DIALOG)
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
            DefaultBrowserPage.DEFAULT_BROWSER_REQUEST_CODE_DIALOG -> {
                val origin =
                    if (resultCode == DefaultBrowserPage.DEFAULT_BROWSER_RESULT_CODE_DIALOG_INTERNAL) {
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
        private const val HEADER_IMAGE_MIN_DP = 180
        private const val HEADER_IMAGE_DESIGN_WIDTH_DP = 514
    }
}
