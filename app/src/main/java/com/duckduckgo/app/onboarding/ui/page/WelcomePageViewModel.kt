/*
 * Copyright (c) 2020 DuckDuckGo
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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.app.global.DefaultRoleBrowserDialogExperiment
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.statistics.pixels.Pixel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class WelcomePageViewModel(
    private val appInstallStore: AppInstallStore,
    private val context: Context,
    private val pixel: Pixel,
    private val defaultRoleBrowserDialogExperiment: DefaultRoleBrowserDialogExperiment
) : ViewModel() {

    fun reduce(event: WelcomePageView.Event): Flow<WelcomePageView.State> {
        return when (event) {
            WelcomePageView.Event.OnPrimaryCtaClicked -> onPrimaryCtaClicked()
            WelcomePageView.Event.OnDefaultBrowserSet -> onDefaultBrowserSet()
            WelcomePageView.Event.OnDefaultBrowserNotSet -> onDefaultBrowserNotSet()
        }
    }

    private fun onPrimaryCtaClicked(): Flow<WelcomePageView.State> = flow {
        if (defaultRoleBrowserDialogExperiment.shouldShowExperiment()) {
            val intent = defaultRoleBrowserDialogExperiment.createIntent(context)
            if (intent != null) {
                emit(WelcomePageView.State.ShowDefaultBrowserDialog(intent))
            } else {
                pixel.fire(Pixel.PixelName.DEFAULT_BROWSER_DIALOG_NOT_SHOWN)
                emit(WelcomePageView.State.Finish)
            }
        } else {
            emit(WelcomePageView.State.Finish)
        }
    }

    private fun onDefaultBrowserSet(): Flow<WelcomePageView.State> = flow {
        defaultRoleBrowserDialogExperiment.experimentShown()

        appInstallStore.defaultBrowser = true

        pixel.fire(
            Pixel.PixelName.DEFAULT_BROWSER_SET,
            mapOf(Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString())
        )

        emit(WelcomePageView.State.Finish)
    }

    private fun onDefaultBrowserNotSet(): Flow<WelcomePageView.State> = flow {
        defaultRoleBrowserDialogExperiment.experimentShown()

        appInstallStore.defaultBrowser = false

        pixel.fire(
            Pixel.PixelName.DEFAULT_BROWSER_NOT_SET,
            mapOf(Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString())
        )

        emit(WelcomePageView.State.Finish)
    }
}

@Suppress("UNCHECKED_CAST")
class WelcomePageViewModelFactory(
    private val appInstallStore: AppInstallStore,
    private val context: Context,
    private val pixel: Pixel,
    private val defaultRoleBrowserDialogExperiment: DefaultRoleBrowserDialogExperiment
) : ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return with(modelClass) {
            when {
                isAssignableFrom(WelcomePageViewModel::class.java) -> WelcomePageViewModel(
                    appInstallStore, context, pixel, defaultRoleBrowserDialogExperiment
                )
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        } as T
    }
}
