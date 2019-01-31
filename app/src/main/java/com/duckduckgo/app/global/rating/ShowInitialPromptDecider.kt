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
import timber.log.Timber


interface ShowPromptDecider {
    suspend fun shouldShowPrompt(): Boolean
}

class InitialPromptDecider(
    private val appDaysUsedRepository: AppDaysUsedRepository,
    private val appEnjoymentRepository: AppEnjoymentRepository
) : ShowPromptDecider {

    override suspend fun shouldShowPrompt(): Boolean {
        if (!enoughDaysPassedToShowFirstPrompt()) {
            Timber.i("Not enough days passed to show first prompt")
            return false
        }

        if (appEnjoymentRepository.hasUserPreviouslySeenFirstPrompt()) {
            Timber.i("User has seen first prompt already")
            return false
        }

        return true
    }

    private suspend fun enoughDaysPassedToShowFirstPrompt(): Boolean {
        val daysUsed = appDaysUsedRepository.getNumberOfDaysAppUsed()
        val enoughDaysUsed = daysUsed >= MINIMUM_DAYS_USAGE_BEFORE_SHOWING_FIRST_PROMPT

        return enoughDaysUsed.also {
            Timber.i("Number of days usage: $daysUsed. Enough days have passed to show app enjoyment prompt: %s", if (enoughDaysUsed) "yes" else "no")
        }
    }

    companion object {
        private const val MINIMUM_DAYS_USAGE_BEFORE_SHOWING_FIRST_PROMPT = 3
    }

}

class SecondaryPromptDecider(
    private val appDaysUsedRepository: AppDaysUsedRepository,
    private val appEnjoymentRepository: AppEnjoymentRepository
) : ShowPromptDecider {

    override suspend fun shouldShowPrompt(): Boolean {
        if (!appEnjoymentRepository.canUserBeShownSecondPrompt()) {
            Timber.i("User should not be shown a second prompt")
            return false
        }

        if (!enoughDaysPassedToShowSecondPrompt()) {
            Timber.i("Not enough days passed to show second prompt")
            return false
        }

        return true
    }

    private suspend fun enoughDaysPassedToShowSecondPrompt(): Boolean {
        val date = appEnjoymentRepository.dateUserDismissedFirstPrompt()
        if (date == null) {
            Timber.i("Couldn't find a previous time first prompt was handled; bailing out here")
            return false
        }

        val daysUsed = appDaysUsedRepository.getNumberOfDaysAppUsedSinceDate(date)

        val enoughDaysUsed = daysUsed >= MINIMUM_DAY_USAGE_SINCE_INITIAL_PROMPT_BEFORE_SECONDARY_PROMPT

        return enoughDaysUsed.also {
            Timber.i(
                "Number of days usage since first prompt dismissed: $daysUsed. Enough days have passed to show second app enjoyment prompt: %s",
                if (enoughDaysUsed) "yes" else "no"
            )
        }
    }

    companion object {
        private const val MINIMUM_DAY_USAGE_SINCE_INITIAL_PROMPT_BEFORE_SECONDARY_PROMPT = 4
    }

}