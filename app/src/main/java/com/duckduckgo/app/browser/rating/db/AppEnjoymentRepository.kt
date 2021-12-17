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

package com.duckduckgo.app.browser.rating.db

import com.duckduckgo.app.global.rating.PromptCount
import java.util.*
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

interface AppEnjoymentRepository {

    suspend fun onUserSelectedToRateApp(promptCount: PromptCount)
    suspend fun onUserDeclinedToRateApp(promptCount: PromptCount)
    suspend fun onUserSelectedToGiveFeedback(promptCount: PromptCount)
    suspend fun onUserDeclinedToGiveFeedback(promptCount: PromptCount)
    suspend fun onUserDeclinedToSayIfEnjoyingApp(promptCount: PromptCount)

    suspend fun canUserBeShownFirstPrompt(): Boolean
    suspend fun canUserBeShownSecondPrompt(): Boolean
    suspend fun dateUserDismissedFirstPrompt(): Date?
}

class AppEnjoymentDatabaseRepository(private val appEnjoymentDao: AppEnjoymentDao) :
    AppEnjoymentRepository {
    private val singleThreadedDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    override suspend fun onUserSelectedToRateApp(promptCount: PromptCount) =
        withContext(singleThreadedDispatcher) {
            appEnjoymentDao.insertEvent(
                AppEnjoymentEntity(AppEnjoymentEventType.USER_PROVIDED_RATING, promptCount))
        }

    override suspend fun onUserDeclinedToRateApp(promptCount: PromptCount) =
        withContext(singleThreadedDispatcher) {
            appEnjoymentDao.insertEvent(
                AppEnjoymentEntity(AppEnjoymentEventType.USER_DECLINED_RATING, promptCount))
        }

    override suspend fun onUserSelectedToGiveFeedback(promptCount: PromptCount) =
        withContext(singleThreadedDispatcher) {
            appEnjoymentDao.insertEvent(
                AppEnjoymentEntity(AppEnjoymentEventType.USER_PROVIDED_FEEDBACK, promptCount))
        }

    override suspend fun onUserDeclinedToGiveFeedback(promptCount: PromptCount) =
        withContext(singleThreadedDispatcher) {
            appEnjoymentDao.insertEvent(
                AppEnjoymentEntity(AppEnjoymentEventType.USER_DECLINED_FEEDBACK, promptCount))
        }

    override suspend fun onUserDeclinedToSayIfEnjoyingApp(promptCount: PromptCount) =
        withContext(singleThreadedDispatcher) {
            appEnjoymentDao.insertEvent(
                AppEnjoymentEntity(
                    AppEnjoymentEventType.USER_DECLINED_TO_SAY_WHETHER_ENJOYING, promptCount))
        }

    override suspend fun canUserBeShownFirstPrompt(): Boolean {
        return withContext(singleThreadedDispatcher) {
            if (appEnjoymentDao.hasUserProvidedRating()) {
                Timber.d("User has given a rating previously")
                return@withContext false
            }

            if (appEnjoymentDao.hasUserProvidedFeedback()) {
                Timber.d("User has provided feedback previously")
                return@withContext false
            }

            val promptCount = PromptCount.first().value

            if (appEnjoymentDao.hasUserDeclinedRating(promptCount)) {
                Timber.d(
                    "User has declined to give rating previously for prompt number $promptCount")
                return@withContext false
            }

            if (appEnjoymentDao.hasUserDeclinedFeedback(promptCount)) {
                Timber.d("User has declined feedback previously for prompt number $promptCount")
                return@withContext false
            }

            if (appEnjoymentDao.hasUserDeclinedToSayWhetherEnjoying(promptCount)) {
                Timber.d(
                    "User has declined to say whether enjoying or not for prompt number $promptCount")
                return@withContext false
            }

            Timber.d("User has not recently responded to app enjoyment prompt number $promptCount")
            return@withContext true
        }
    }

    override suspend fun canUserBeShownSecondPrompt(): Boolean {
        return withContext(singleThreadedDispatcher) {
            if (appEnjoymentDao.hasUserProvidedRating()) {
                Timber.d("User has given a rating previously")
                return@withContext false
            }

            if (appEnjoymentDao.hasUserProvidedFeedback()) {
                Timber.d("User has provided feedback previously")
                return@withContext false
            }

            val secondPrompt = PromptCount.second().value

            if (appEnjoymentDao.hasUserDeclinedFeedback(secondPrompt)) {
                Timber.d("User has already declined feedback for second prompt previously")
                return@withContext false
            }

            if (appEnjoymentDao.hasUserDeclinedRating(secondPrompt)) {
                Timber.d("User has already declined rating for second prompt previously")
                return@withContext false
            }

            if (appEnjoymentDao.hasUserDeclinedToSayWhetherEnjoying(secondPrompt)) {
                Timber.d(
                    "User has already declined to say whether enjoying the app or not previously")
                return@withContext false
            }

            Timber.d(
                "User has not recently provided a rating or feedback; they can be shown another prompt")
            return@withContext true
        }
    }

    override suspend fun dateUserDismissedFirstPrompt(): Date? {
        return withContext(singleThreadedDispatcher) {
            val declinedDate = appEnjoymentDao.latestDateUserDeclinedRatingOrFeedback()
            if (declinedDate == null) {
                Timber.d("Never declined rating nor feedback before")
                return@withContext null
            }

            return@withContext Date(declinedDate)
        }
    }
}
