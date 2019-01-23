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

package com.duckduckgo.app.global.rating

import android.content.Context
import androidx.annotation.UiThread
import androidx.lifecycle.*
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.global.rating.AppEnjoyment.AppEnjoymentPromptOptions.*
import com.duckduckgo.app.playstore.PlayStoreUtils
import timber.log.Timber

interface AppEnjoymentManager : LifecycleObserver {
    fun onUserEnjoyingApp()
    fun onUserNotEnjoyingApp()
    fun onUserSelectedToRateApp()
    fun userDeclinedToRateApp()
    fun onUserDeclinedToGiveFeedback()
    fun onUserSelectedToGiveFeedback()

    val promptType: LiveData<AppEnjoyment.AppEnjoymentPromptOptions>
}

class AppEnjoyment(private val playStoreUtils: PlayStoreUtils, private val context: Context) : AppEnjoymentManager {

    private val _promptType: MutableLiveData<AppEnjoymentPromptOptions> = MutableLiveData<AppEnjoymentPromptOptions>()
        .also { it.value = AppEnjoymentPromptOptions.ShowNothing }

    override val promptType: LiveData<AppEnjoymentPromptOptions>
        get() = _promptType

    @UiThread
    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onAppCreation() {
        Timber.i("On app creation")

        _promptType.value = determineInitialPromptType()
    }

    private fun determineInitialPromptType(): AppEnjoymentPromptOptions {
        if (!playStoreUtils.isPlayStoreInstalled(context)) {
            Timber.i("Play Store is not installed; cannot show ratings app enjoyment prompts")
            return ShowNothing
        }

        if (!playStoreUtils.installedFromPlayStore(context)) {
            Timber.i("DuckDuckGo was not installed from Play Store")

            if (BuildConfig.DEBUG) {
                Timber.i("Running in DEBUG mode so will allow this; would normally enforce this check")
            } else {
                Timber.i("Cannot show app enjoyment prompts")
                return ShowNothing
            }
        }

        // some other logic to determine whether to show it or not
        return ShowEnjoymentPrompt
    }

    override fun onUserEnjoyingApp() {
        Timber.i("User is enjoying app; asking for rating")
        _promptType.value = ShowRatingPrompt
    }

    override fun onUserNotEnjoyingApp() {
        Timber.i("User is not enjoying app; asking for feedback")
        _promptType.value = ShowFeedbackPrompt
    }

    override fun onUserSelectedToRateApp() {
        hideAllPrompts()
    }

    override fun userDeclinedToRateApp() {
        hideAllPrompts()
    }

    override fun onUserDeclinedToGiveFeedback() {
        hideAllPrompts()
    }

    override fun onUserSelectedToGiveFeedback() {
        hideAllPrompts()
    }

    private fun hideAllPrompts() {
        _promptType.value = ShowNothing
    }

    sealed class AppEnjoymentPromptOptions {

        object ShowNothing : AppEnjoymentPromptOptions()
        object ShowEnjoymentPrompt : AppEnjoymentPromptOptions()
        object ShowFeedbackPrompt : AppEnjoymentPromptOptions()
        object ShowRatingPrompt : AppEnjoymentPromptOptions()

    }

}