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

package com.duckduckgo.app.onboarding.store

import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.DispatcherProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import logcat.LogPriority.DEBUG
import logcat.logcat

interface UserStageStore {
    fun userAppStageFlow(): Flow<AppStage>
    suspend fun getUserAppStage(): AppStage
    suspend fun stageCompleted(appStage: AppStage): AppStage
    suspend fun moveToStage(appStage: AppStage)
    val currentAppStage: Flow<AppStage>
}

class AppUserStageStore @Inject constructor(
    private val userStageDao: UserStageDao,
    private val dispatcher: DispatcherProvider,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
) : UserStageStore {

    override fun userAppStageFlow(): Flow<AppStage> {
        return userStageDao.currentUserAppStageFlow().map { it?.appStage ?: AppStage.NEW }
    }

    override suspend fun getUserAppStage(): AppStage {
        return withContext(dispatcher.io()) {
            val userStage = userStageDao.currentUserAppStage()
            return@withContext userStage?.appStage ?: AppStage.NEW
        }
    }

    override suspend fun stageCompleted(appStage: AppStage): AppStage {
        return withContext(dispatcher.io()) {
            val currentStage = getUserAppStage()
            if (currentStage == AppStage.ESTABLISHED &&
                androidBrowserConfigFeature.establishedAppStageGuard().isEnabled()
            ) {
                logcat(DEBUG) { "UserStageStore: User is already ESTABLISHED, returning ESTABLISHED" }
                return@withContext AppStage.ESTABLISHED
            }

            val newAppStage = when (appStage) {
                AppStage.NEW -> AppStage.DAX_ONBOARDING
                AppStage.DAX_ONBOARDING -> AppStage.ESTABLISHED
                AppStage.ESTABLISHED -> AppStage.ESTABLISHED
            }

            if (newAppStage != appStage) {
                logcat(DEBUG) { "UserStageStore: currentStage=$currentStage, newAppStage=$newAppStage" }
                userStageDao.updateUserStage(newAppStage)
            }

            return@withContext newAppStage
        }
    }

    override suspend fun moveToStage(appStage: AppStage) {
        userStageDao.updateUserStage(appStage)
    }

    override val currentAppStage: Flow<AppStage> = userStageDao.currentAppStage().map { it.appStage }
}

suspend fun UserStageStore.isNewUser(): Boolean {
    return this.getUserAppStage() == AppStage.NEW
}

suspend fun UserStageStore.daxOnboardingActive(): Boolean {
    return this.getUserAppStage() == AppStage.DAX_ONBOARDING
}
