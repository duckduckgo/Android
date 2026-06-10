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

import com.duckduckgo.app.global.rating.AppEnjoymentPromptOptions.ShowEnjoymentPrompt
import com.duckduckgo.app.global.rating.AppEnjoymentPromptOptions.ShowNothing
import com.duckduckgo.app.usage.search.SearchCountDao
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.playstore.PlayStoreUtils
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.logcat

interface PromptTypeDecider {
    suspend fun determineInitialPromptType(): AppEnjoymentPromptOptions
}

class InitialPromptTypeDecider(
    private val playStoreUtils: PlayStoreUtils,
    private val searchCountDao: SearchCountDao,
    private val initialPromptDecider: ShowPromptDecider,
    private val secondaryPromptDecider: ShowPromptDecider,
    private val dispatchers: DispatcherProvider,
    private val appBuildConfig: AppBuildConfig,
) : PromptTypeDecider {

    override suspend fun determineInitialPromptType(): AppEnjoymentPromptOptions {
        return withContext(dispatchers.io()) {
            if (!isPlayStoreInstalled()) return@withContext ShowNothing
            if (!wasInstalledThroughPlayStore()) return@withContext ShowNothing
            if (!enoughSearchesMade()) return@withContext ShowNothing

            if (initialPromptDecider.shouldShowPrompt()) {
                logcat(INFO) { "Will show app enjoyment prompt for first time" }
                return@withContext ShowEnjoymentPrompt(PromptCount.first())
            }

            logcat(INFO) { "Decided not to show any app enjoyment prompts" }
            return@withContext ShowNothing
        }
    }

    private fun isPlayStoreInstalled(): Boolean {
        if (!playStoreUtils.isPlayStoreInstalled()) {
            logcat(INFO) { "Play Store is not installed; cannot show ratings app enjoyment prompts" }
            return false
        }
        return true
    }

    private fun wasInstalledThroughPlayStore(): Boolean {
        if (!playStoreUtils.installedFromPlayStore()) {
            logcat(INFO) { "DuckDuckGo was not installed from Play Store" }

            return if (appBuildConfig.isDebug) {
                logcat(INFO) { "Running in DEBUG mode so will allow this; would normally enforce this check" }
                true
            } else {
                logcat(INFO) { "Cannot show app enjoyment prompts" }
                false
            }
        }

        return true
    }

    private fun enoughSearchesMade(): Boolean {
        val numberSearchesMade = searchCountDao.getSearchesMade()
        val enoughMade = numberSearchesMade >= MINIMUM_SEARCHES_THRESHOLD

        logcat(INFO) { "Searches made: $numberSearchesMade. Enough searches made to show app enjoyment prompt: ${if (enoughMade) "yes" else "no"}" }
        return enoughMade
    }
}

sealed class AppEnjoymentPromptOptions {

    data object ShowNothing : AppEnjoymentPromptOptions()
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
