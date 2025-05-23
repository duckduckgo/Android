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

import com.duckduckgo.app.browser.rating.db.AppEnjoymentRepository
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import logcat.LogPriority.INFO
import logcat.logcat

interface ShowPromptDecider {
    suspend fun shouldShowPrompt(): Boolean
}

class InitialPromptDecider(
    private val appDaysUsedRepository: AppDaysUsedRepository,
    private val appEnjoymentRepository: AppEnjoymentRepository,
) : ShowPromptDecider {

    override suspend fun shouldShowPrompt(): Boolean {
        if (!enoughDaysPassedToShowFirstPrompt()) {
            logcat(INFO) { "Not enough days passed to show first prompt" }
            return false
        }

        if (!appEnjoymentRepository.canUserBeShownFirstPrompt()) {
            logcat(INFO) { "User has seen first prompt already" }
            return false
        }

        return true
    }

    private suspend fun enoughDaysPassedToShowFirstPrompt(): Boolean {
        val daysUsed = appDaysUsedRepository.getNumberOfDaysAppUsed()
        val enoughDaysUsed = daysUsed >= MINIMUM_DAYS_USAGE_BEFORE_FIRST_PROMPT

        logcat(INFO) { "Number of days usage: $daysUsed. Enough to show app enjoyment prompt: ${if (enoughDaysUsed) "yes" else "no"}" }
        return enoughDaysUsed
    }
}

class SecondaryPromptDecider(
    private val appDaysUsedRepository: AppDaysUsedRepository,
    private val appEnjoymentRepository: AppEnjoymentRepository,
) : ShowPromptDecider {

    override suspend fun shouldShowPrompt(): Boolean {
        if (!appEnjoymentRepository.canUserBeShownSecondPrompt()) {
            logcat(INFO) { "User should not be shown a second prompt" }
            return false
        }

        if (!enoughDaysPassedToShowSecondPrompt()) {
            logcat(INFO) { "Not enough days passed to show second prompt" }
            return false
        }

        return true
    }

    private suspend fun enoughDaysPassedToShowSecondPrompt(): Boolean {
        val date = appEnjoymentRepository.dateUserDismissedFirstPrompt()
        if (date == null) {
            logcat { "Couldn't find a previous time first prompt was handled; must never have been shown" }
            return false
        }

        val daysUsed = appDaysUsedRepository.getNumberOfDaysAppUsedSinceDate(date)
        val enoughDaysUsed = daysUsed >= MINIMUM_DAYS_USAGE_SINCE_INITIAL_PROMPT_BEFORE_SECOND_PROMPT

        logcat(INFO) { "Days since first prompt dismissed: $daysUsed. Enough to show 2nd prompt: ${if (enoughDaysUsed) "yes" else "no"}" }

        return enoughDaysUsed
    }
}
