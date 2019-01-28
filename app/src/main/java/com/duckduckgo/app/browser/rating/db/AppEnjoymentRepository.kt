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

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.Executors


interface AppEnjoymentRepository {

    suspend fun onUserSelectedToRateApp()
    suspend fun onUserDeclinedToRateApp()
    suspend fun onUserSelectedToGiveFeedback()
    suspend fun onUserDeclinedToGiveFeedback()

    suspend fun hasUserRecentlyRespondedToAppEnjoymentPrompt(): Boolean
}

class AppEnjoymentDatabaseRepository(private val appEnjoymentDao: AppEnjoymentDao) : AppEnjoymentRepository {
    private val singleThreadedDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    override suspend fun onUserSelectedToRateApp() = withContext(singleThreadedDispatcher) {
        appEnjoymentDao.insertEvent(AppEnjoymentEntity(AppEnjoymentEventType.USER_PROVIDED_RATING))
    }

    override suspend fun onUserDeclinedToRateApp() = withContext(singleThreadedDispatcher) {
        appEnjoymentDao.insertEvent(AppEnjoymentEntity(AppEnjoymentEventType.USER_DECLINED_RATING))
    }

    override suspend fun onUserSelectedToGiveFeedback() = withContext(singleThreadedDispatcher) {
        appEnjoymentDao.insertEvent(AppEnjoymentEntity(AppEnjoymentEventType.USER_PROVIDED_FEEDBACK))
    }

    override suspend fun onUserDeclinedToGiveFeedback() = withContext(singleThreadedDispatcher) {
        appEnjoymentDao.insertEvent(AppEnjoymentEntity(AppEnjoymentEventType.USER_DECLINED_FEEDBACK))
    }

    override suspend fun hasUserRecentlyRespondedToAppEnjoymentPrompt(): Boolean {
        return withContext(singleThreadedDispatcher) {
            if (appEnjoymentDao.hasUserProvidedRating()) {
                Timber.d("User has given a rating previously")
                return@withContext true
            }

            if (appEnjoymentDao.hasUserDeclinedRating()) {
                Timber.d("User has declined to give rating previously")
                return@withContext true
            }

            if (appEnjoymentDao.hasUserProvidedFeedback()) {
                Timber.d("User has provided feedback previously")
                return@withContext true

            }
            if (appEnjoymentDao.hasUserDeclinedFeedback()) {
                Timber.d("User has declined feedback previously")
                return@withContext true
            }

            Timber.d("User has not recently responded to app enjoyment prompts")
            return@withContext false
        }
    }
}