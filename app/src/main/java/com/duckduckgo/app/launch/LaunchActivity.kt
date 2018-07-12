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

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.onboarding.ui.OnboardingActivity
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelDefinition.*
import timber.log.Timber
import javax.inject.Inject


class LaunchActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var pixel: Pixel

    private val viewModel: LaunchViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(LaunchViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch)
        configureObservers()
    }

    override fun onStart() {
        super.onStart()
        Timber.d("LaunchActivity onStart")
        pixel.fire(APP_LAUNCH)
    }

    private fun configureObservers() {
        viewModel.command.observe(this, Observer {
            processCommand(it)
        })
    }

    private fun processCommand(it: LaunchViewModel.Command?) {
        when (it) {
            LaunchViewModel.Command.Onboarding -> {
                showHome(showOnboarding = true)
            }
            is LaunchViewModel.Command.Home -> {
                showHome(showOnboarding = false)
            }
        }
    }

    private fun showHome(showOnboarding: Boolean) {
        startActivity(BrowserActivity.intent(this))

        if (showOnboarding) {
            startActivity(OnboardingActivity.intent(this))
        }

        finish()
    }
}