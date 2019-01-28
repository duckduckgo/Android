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
import com.duckduckgo.app.browser.rating.db.AppEnjoymentRepository
import com.duckduckgo.app.playstore.PlayStoreUtils
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import com.duckduckgo.app.usage.search.SearchCountDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

interface AppEnjoymentManager : LifecycleObserver {
    fun onUserEnjoyingApp()
    fun onUserNotEnjoyingApp()
    suspend fun onUserSelectedToRateApp()
    suspend fun userDeclinedToRateApp()
    suspend fun onUserDeclinedToGiveFeedback()
    suspend fun onUserSelectedToGiveFeedback()

    val promptType: LiveData<AppEnjoyment.AppEnjoymentPromptOptions>
}

class AppEnjoyment(
    private val playStoreUtils: PlayStoreUtils,
    private val searchCountDao: SearchCountDao,
    private val appDaysUsedRepository: AppDaysUsedRepository,
    private val appEnjoymentRepository: AppEnjoymentRepository,
    private val context: Context
) :
    AppEnjoymentManager {

    private val _promptType: MutableLiveData<AppEnjoymentPromptOptions> = MutableLiveData<AppEnjoymentPromptOptions>()
        .also { it.value = AppEnjoymentPromptOptions.ShowNothing }

    override val promptType: LiveData<AppEnjoymentPromptOptions>
        get() = _promptType

    private var isFreshAppCreation = false

    @UiThread
    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onAppCreation() {
        Timber.i("On app creation")
        isFreshAppCreation = true
    }

    @UiThread
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppStart() {
        Timber.i("On app start")

        if (isFreshAppCreation) {
            GlobalScope.launch(Dispatchers.Main) {
                val type = determineInitialPromptType()
                _promptType.value = type
            }
            isFreshAppCreation = false
        }
    }

    private suspend fun determineInitialPromptType(): AppEnjoymentPromptOptions {
        return withContext(Dispatchers.IO) {

            if (!playStoreUtils.isPlayStoreInstalled(context)) {
                Timber.i("Play Store is not installed; cannot show ratings app enjoyment prompts")
                return@withContext AppEnjoymentPromptOptions.ShowNothing
            }

            if (!playStoreUtils.installedFromPlayStore(context)) {
                Timber.i("DuckDuckGo was not installed from Play Store")

                if (BuildConfig.DEBUG) {
                    Timber.i("Running in DEBUG mode so will allow this; would normally enforce this check")
                } else {
                    Timber.i("Cannot show app enjoyment prompts")
                    return@withContext AppEnjoymentPromptOptions.ShowNothing
                }
            }

            if (enoughSearchesMade() && enoughDaysPassed() && !userRecentlyTookAction()) {
                return@withContext AppEnjoymentPromptOptions.ShowEnjoymentPrompt
            }

            Timber.d("Decided not to show any prompts")

            return@withContext AppEnjoymentPromptOptions.ShowNothing
        }
    }

    private suspend fun userRecentlyTookAction(): Boolean {
        return appEnjoymentRepository.hasUserRecentlyRespondedToAppEnjoymentPrompt()
    }

    private suspend fun enoughDaysPassed(): Boolean {
        val daysUsed = appDaysUsedRepository.getNumberOfDaysAppUsed()
        val enoughDaysUsed = daysUsed >= MINIMUM_DAYS_USED_THRESHOLD

        return enoughDaysUsed.also {
            Timber.i("Number of days usage: $daysUsed. Enough days have passed to show app enjoyment prompt: %s", if (enoughDaysUsed) "yes" else "no")
        }
    }

    private fun enoughSearchesMade(): Boolean {
        val numberSearchesMade = searchCountDao.getSearchesMade()
        val enoughMade = numberSearchesMade >= MINIMUM_SEARCHES_THRESHOLD

        return enoughMade.also {
            Timber.i("Searches made: $numberSearchesMade. Enough searches made to show app enjoyment prompt: %s", if (enoughMade) "yes" else "no")
        }
    }

    override fun onUserEnjoyingApp() {
        Timber.i("User is enjoying app; asking for rating")
        _promptType.value = AppEnjoymentPromptOptions.ShowRatingPrompt
    }

    override fun onUserNotEnjoyingApp() {
        Timber.i("User is not enjoying app; asking for feedback")
        _promptType.value = AppEnjoymentPromptOptions.ShowFeedbackPrompt
    }

    override suspend fun onUserSelectedToRateApp() {
        hideAllPrompts()

        appEnjoymentRepository.onUserSelectedToRateApp()

        Timber.i("Recorded that user selected to rate app")
    }

    override suspend fun userDeclinedToRateApp() {
        hideAllPrompts()

        appEnjoymentRepository.onUserDeclinedToRateApp()

        Timber.i("Recorded that user declined to rate app")
    }

    override suspend fun onUserSelectedToGiveFeedback() {
        hideAllPrompts()

        appEnjoymentRepository.onUserSelectedToGiveFeedback()

        Timber.i("Recorded that user selected to give feedback")
    }

    override suspend fun onUserDeclinedToGiveFeedback() {
        hideAllPrompts()

        appEnjoymentRepository.onUserDeclinedToGiveFeedback()

        Timber.i("Recorded that user declined to give feedback")
    }

    private fun hideAllPrompts() {
        _promptType.value = AppEnjoymentPromptOptions.ShowNothing
    }

    companion object {

        // todo change this to 5
        private const val MINIMUM_SEARCHES_THRESHOLD = 0

        // todo change this to 3
        private const val MINIMUM_DAYS_USED_THRESHOLD = 0
    }

    sealed class AppEnjoymentPromptOptions {

        object ShowNothing : AppEnjoymentPromptOptions()
        object ShowEnjoymentPrompt : AppEnjoymentPromptOptions()
        object ShowFeedbackPrompt : AppEnjoymentPromptOptions()
        object ShowRatingPrompt : AppEnjoymentPromptOptions()

    }

}