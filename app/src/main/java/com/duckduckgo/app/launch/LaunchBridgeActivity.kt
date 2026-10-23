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

package com.duckduckgo.app.launch

import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.mode.AppLauncher
import com.duckduckgo.app.install.OutOfSightLaunchDetector
import com.duckduckgo.app.onboarding.ui.OnboardingActivity
import com.duckduckgo.app.pixels.AppReturnPixelSender
import com.duckduckgo.app.pixels.toPixelLaunchSourceValue
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class LaunchBridgeActivity : DuckDuckGoActivity() {

    private val viewModel: LaunchViewModel by bindViewModel()

    @Inject
    lateinit var appReturnPixelSender: AppReturnPixelSender

    @Inject
    lateinit var outOfSightLaunchDetector: OutOfSightLaunchDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        if (outOfSightLaunchDetector.onLaunchDisplay(launchDisplay())) {
            // Nobody sees this launch, and the store wipes the app's data afterwards. Starting onboarding would only leave the
            // reinstall marker behind and send pixels for a launch the user never made.
            finish()
            return
        }
        splashScreen.setKeepOnScreenCondition { true }

        appReturnPixelSender.fireIfNeeded(AppLauncher.toPixelLaunchSourceValue())

        setContentView(R.layout.activity_launch)

        configureObservers()

        viewModel.start(intent)
    }

    private fun launchDisplay(): Display? =
        if (Build.VERSION.SDK_INT >= 30) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }

    private fun configureObservers() {
        viewModel.command.observe(this) {
            processCommand(it)
        }
    }

    private fun processCommand(it: LaunchViewModel.Command) {
        when (it) {
            is LaunchViewModel.Command.Onboarding -> showOnboarding()
            is LaunchViewModel.Command.Home -> showHome()
        }
    }

    private fun showOnboarding() {
        startActivity(OnboardingActivity.intent(this))
        finish()
    }

    private fun showHome() {
        startActivity(BrowserActivity.intent(this, launchSource = AppLauncher))
        overridePendingTransition(0, 0)
        finish()
    }
}
