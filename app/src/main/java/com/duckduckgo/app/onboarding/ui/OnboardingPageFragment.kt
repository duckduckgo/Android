/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserSystemSettings
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.*
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.content_onboarding_default_browser.*
import timber.log.Timber
import javax.inject.Inject

sealed class OnboardingPageFragment : Fragment() {

    @ColorRes
    abstract fun backgroundColor(): Int

    @LayoutRes
    abstract fun layoutResource(): Int

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(layoutResource(), container, false)

    class ProtectDataPage : OnboardingPageFragment() {
        override fun layoutResource(): Int = R.layout.content_onboarding_protect_data
        override fun backgroundColor(): Int = R.color.lightOliveGreen
    }

    class NoTracePage : OnboardingPageFragment() {
        override fun layoutResource(): Int = R.layout.content_onboarding_no_trace
        override fun backgroundColor(): Int = R.color.cornflowerBlue
    }

    class DefaultBrowserPage : OnboardingPageFragment() {
        override fun layoutResource(): Int = R.layout.content_onboarding_default_browser
        override fun backgroundColor(): Int = R.color.eastBay

        @Inject
        lateinit var pixel: Pixel

        @Inject
        lateinit var defaultBrowserDetector: DefaultBrowserDetector

        override fun onAttach(context: Context?) {
            AndroidSupportInjection.inject(this)
            super.onAttach(context)
        }

        override fun onActivityCreated(savedInstanceState: Bundle?) {
            super.onActivityCreated(savedInstanceState)
            launchSettingsButton.setOnClickListener { onLaunchDefaultBrowserSettingsClicked() }
        }

        override fun setUserVisibleHint(isVisibleToUser: Boolean) {
            super.setUserVisibleHint(isVisibleToUser)
            if (isVisibleToUser) {
                pixel.fire(DEFAULT_BROWSER_INFO_VIEWED)
            }
        }

        private fun onLaunchDefaultBrowserSettingsClicked() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val intent = DefaultBrowserSystemSettings.intent()
                try {
                    startActivityForResult(intent, DEFAULT_BROWSER_REQUEST_CODE)
                } catch (e: ActivityNotFoundException) {
                    Timber.w(e, getString(R.string.cannotLaunchDefaultAppSettings))
                }
            }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            when (requestCode) {
                DEFAULT_BROWSER_REQUEST_CODE -> {
                    fireDefaultBrowserPixel()
                }
                else -> super.onActivityResult(requestCode, resultCode, data)
            }
        }

        private fun fireDefaultBrowserPixel() {
            if (defaultBrowserDetector.isCurrentlyConfiguredAsDefaultBrowser()) {
                Timber.i("User returned from default settings; DDG is now the default")
                pixel.fire(DEFAULT_BROWSER_SET)
            } else {
                Timber.i("User returned from default settings; DDG was not set default")
                pixel.fire(DEFAULT_BROWSER_NOT_SET)
            }
        }

        companion object {
            private const val DEFAULT_BROWSER_REQUEST_CODE = 100
        }
    }
}