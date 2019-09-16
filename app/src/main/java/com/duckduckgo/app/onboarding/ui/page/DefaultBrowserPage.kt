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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserSystemSettings
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.*
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.content_onboarding_default_browser.*
import timber.log.Timber
import javax.inject.Inject


class DefaultBrowserPage : OnboardingPageFragment() {
    override fun layoutResource(): Int = R.layout.content_onboarding_default_browser

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var installStore: AppInstallStore

    @Inject
    lateinit var defaultBrowserDetector: DefaultBrowserDetector

    private var userLaunchedDefaultBrowserSettings = false

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (savedInstanceState != null) {
            userLaunchedDefaultBrowserSettings = savedInstanceState.getBoolean(SAVED_STATE_LAUNCHED_SETTINGS)
        }

        extractContinueButtonTextResourceId()?.let { continueButton.setText(it) }

        launchSettingsButton.setOnClickListener {
            onLaunchDefaultBrowserSettingsClicked()
        }
        continueButton.setOnClickListener {
            if (!userLaunchedDefaultBrowserSettings) {
                pixel.fire(PixelName.ONBOARDING_DEFAULT_BROWSER_SKIPPED)
            }
            onContinuePressed()
        }
        pixel.fire(PixelName.ONBOARDING_DEFAULT_BROWSER_VISUALIZED)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(SAVED_STATE_LAUNCHED_SETTINGS, userLaunchedDefaultBrowserSettings)
    }

    private fun onLaunchDefaultBrowserSettingsClicked() {
        userLaunchedDefaultBrowserSettings = true
        val params = mapOf(
            PixelParameter.DEFAULT_BROWSER_BEHAVIOUR_TRIGGERED to PixelValues.DEFAULT_BROWSER_SETTINGS
        )
        pixel.fire(PixelName.ONBOARDING_DEFAULT_BROWSER_LAUNCHED, params)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val intent = DefaultBrowserSystemSettings.intent()
            try {
                startActivityForResult(
                    intent,
                    DEFAULT_BROWSER_REQUEST_CODE
                )
            } catch (e: ActivityNotFoundException) {
                Timber.w(e, getString(R.string.cannotLaunchDefaultAppSettings))
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            DEFAULT_BROWSER_REQUEST_CODE -> {
                handleDefaultBrowserResult()
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun handleDefaultBrowserResult() {
        val isDefault = defaultBrowserDetector.isDefaultBrowser()
        val setText = if (isDefault) "was" else "was not"
        Timber.i("User returned from default settings; DDG $setText set as default")

        if (isDefault) {
            installStore.defaultBrowser = true
            val params = mapOf(
                PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString(),
                PixelParameter.DEFAULT_BROWSER_SET_ORIGIN to PixelValues.DEFAULT_BROWSER_SETTINGS
            )
            pixel.fire(PixelName.DEFAULT_BROWSER_SET, params)
        } else {
            val params = mapOf(
                PixelParameter.DEFAULT_BROWSER_SET_ORIGIN to PixelValues.DEFAULT_BROWSER_SETTINGS
            )
            pixel.fire(PixelName.DEFAULT_BROWSER_NOT_SET, params)
        }
    }

    companion object {
        private const val DEFAULT_BROWSER_REQUEST_CODE = 100
        private const val SAVED_STATE_LAUNCHED_SETTINGS = "SAVED_STATE_LAUNCHED_SETTINGS"
    }
}