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
import timber.log.Timber

interface AppEnjoymentUserEventRecorder {

    fun onUserEnjoyingApp(promptCount: PromptCount)
    fun onUserNotEnjoyingApp(promptCount: PromptCount)
    suspend fun onUserSelectedToRateApp(promptCount: PromptCount)
    suspend fun userDeclinedToRateApp(promptCount: PromptCount)
    suspend fun onUserSelectedToGiveFeedback(promptCount: PromptCount)
    suspend fun onUserDeclinedToGiveFeedback(promptCount: PromptCount)
    suspend fun onUserDeclinedToSayIfEnjoyingApp(promptCount: PromptCount)
}

class AppEnjoymentUserEventDatabaseRecorder(
    private val appEnjoymentRepository: AppEnjoymentRepository,
    private val appEnjoymentPromptEmitter: AppEnjoymentPromptEmitter
) : AppEnjoymentUserEventRecorder {

    override fun onUserEnjoyingApp(promptCount: PromptCount) {
        Timber.i("User is enjoying app; asking for rating")
        appEnjoymentPromptEmitter.promptType.value =
            AppEnjoymentPromptOptions.ShowRatingPrompt(promptCount)
    }

    override fun onUserNotEnjoyingApp(promptCount: PromptCount) {
        Timber.i("User is not enjoying app; asking for feedback")
        appEnjoymentPromptEmitter.promptType.value =
            AppEnjoymentPromptOptions.ShowFeedbackPrompt(promptCount)
    }

    override suspend fun onUserSelectedToRateApp(promptCount: PromptCount) {
        hideAllPrompts()

        appEnjoymentRepository.onUserSelectedToRateApp(promptCount)

        Timber.i("Recorded that user selected to rate app")
    }

    override suspend fun userDeclinedToRateApp(promptCount: PromptCount) {
        hideAllPrompts()

        appEnjoymentRepository.onUserDeclinedToRateApp(promptCount)

        Timber.i("Recorded that user declined to rate app")
    }

    override suspend fun onUserSelectedToGiveFeedback(promptCount: PromptCount) {
        hideAllPrompts()

        appEnjoymentRepository.onUserSelectedToGiveFeedback(promptCount)

        Timber.i("Recorded that user selected to give feedback")
    }

    override suspend fun onUserDeclinedToGiveFeedback(promptCount: PromptCount) {
        hideAllPrompts()

        appEnjoymentRepository.onUserDeclinedToGiveFeedback(promptCount)

        Timber.i("Recorded that user declined to give feedback")
    }

    override suspend fun onUserDeclinedToSayIfEnjoyingApp(promptCount: PromptCount) {
        hideAllPrompts()

        appEnjoymentRepository.onUserDeclinedToSayIfEnjoyingApp(promptCount)

        Timber.i("Recorded that user didn't want to participate in app enjoyment")
    }

    private fun hideAllPrompts() {
        appEnjoymentPromptEmitter.promptType.value = AppEnjoymentPromptOptions.ShowNothing
    }
}
