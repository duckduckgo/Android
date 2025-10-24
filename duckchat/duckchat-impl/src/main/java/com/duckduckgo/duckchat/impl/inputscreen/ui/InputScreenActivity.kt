/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.inputscreen.ui

import android.content.res.Configuration
import android.os.Build.VERSION
import android.os.Bundle
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.inputscreen.BrowserAndInputScreenTransitionProvider
import com.duckduckgo.duckchat.api.inputscreen.InputScreenActivityParams
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnel
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(InputScreenActivityParams::class)
class InputScreenActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var browserAndInputScreenTransitionProvider: BrowserAndInputScreenTransitionProvider

    @Inject
    lateinit var inputScreenDiscoveryFunnel: InputScreenDiscoveryFunnel

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var inputScreenConfigResolver: InputScreenConfigResolver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_screen)
        inputScreenDiscoveryFunnel.onInputScreenOpened()
        val params =
            mapOf(
                "orientation" to
                    if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        "landscape"
                    } else {
                        "portrait"
                    },
            )
        pixel.fire(pixel = DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_TEXT_AREA_FOCUSED, parameters = params)
    }

    override fun finish() {
        super.finish()
        applyExitTransition()
    }

    private fun applyExitTransition() {
        val enterTransition = browserAndInputScreenTransitionProvider.getBrowserEnterAnimation(
            inputScreenConfigResolver.isTopOmnibar,
            inputScreenConfigResolver.duckAiToggleVisible,
        )
        val exitTransition = browserAndInputScreenTransitionProvider.getInputScreenExitAnimation(
            inputScreenConfigResolver.isTopOmnibar,
            inputScreenConfigResolver.duckAiToggleVisible,
        )

        if (VERSION.SDK_INT >= 34) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                enterTransition,
                exitTransition,
            )
        } else {
            overridePendingTransition(
                enterTransition,
                exitTransition,
            )
        }
    }
}
