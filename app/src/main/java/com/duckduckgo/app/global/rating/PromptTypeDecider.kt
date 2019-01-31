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
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.playstore.PlayStoreUtils
import com.duckduckgo.app.usage.search.SearchCountDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber


interface PromptTypeDecider {
    suspend fun determineInitialPromptType(): AppEnjoymentPromptOptions
}

class InitialPromptTypeDecider(
    private val playStoreUtils: PlayStoreUtils,
    private val searchCountDao: SearchCountDao,
    private val initialPromptDecider: ShowPromptDecider,
    private val secondaryPromptDecider: ShowPromptDecider,
    private val context: Context
) : PromptTypeDecider {

    override suspend fun determineInitialPromptType(): AppEnjoymentPromptOptions {
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

            if (!enoughSearchesMade()) {
                Timber.i("Not enough searches made to show prompt")
                return@withContext AppEnjoymentPromptOptions.ShowNothing
            }

            if (initialPromptDecider.shouldShowPrompt()) {
                Timber.i("Should show prompt to user for the first time")
                return@withContext AppEnjoymentPromptOptions.ShowEnjoymentPrompt(PromptCount.first())
            } else if (secondaryPromptDecider.shouldShowPrompt()) {
                Timber.i("Should show prompt to user for the second time")
                return@withContext AppEnjoymentPromptOptions.ShowEnjoymentPrompt(PromptCount.second())
            }
            Timber.i("Decided not to show any prompts")
            return@withContext AppEnjoymentPromptOptions.ShowNothing
        }
    }

    private fun enoughSearchesMade(): Boolean {
        val numberSearchesMade = searchCountDao.getSearchesMade()
        val enoughMade = numberSearchesMade >= MINIMUM_SEARCHES_THRESHOLD

        return enoughMade.also {
            Timber.i("Searches made: $numberSearchesMade. Enough searches made to show app enjoyment prompt: %s", if (enoughMade) "yes" else "no")
        }
    }

    companion object {
        private const val MINIMUM_SEARCHES_THRESHOLD = 1
    }
}

sealed class AppEnjoymentPromptOptions {

    object ShowNothing : AppEnjoymentPromptOptions()
    data class ShowEnjoymentPrompt(val promptCount: PromptCount) : AppEnjoymentPromptOptions()
    data class ShowFeedbackPrompt(val promptCount: PromptCount) : AppEnjoymentPromptOptions()
    data class ShowRatingPrompt(val promptCount: PromptCount) : AppEnjoymentPromptOptions()
}

data class PromptCount(val value: Int) {

    companion object {
        fun first(): PromptCount = PromptCount(1)
        fun second(): PromptCount = PromptCount(2)
    }
}