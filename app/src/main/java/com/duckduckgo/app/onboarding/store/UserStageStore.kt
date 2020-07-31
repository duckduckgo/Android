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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.statistics.VariantManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface UserStageStore : LifecycleObserver {
    suspend fun getUserAppStage(): AppStage
    suspend fun stageCompleted(appStage: AppStage): AppStage
    suspend fun moveToStage(appStage: AppStage)
}

class AppUserStageStore @Inject constructor(
    private val userStageDao: UserStageDao,
    private val dispatcher: DispatcherProvider,
    private val variantManager: VariantManager,
    private val appInstallStore: AppInstallStore
) : UserStageStore, LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onAppResumed() {
        GlobalScope.launch(dispatcher.io()) {
            moveUserToEstablished3DaysAfterInstall()
        }
    }

    override suspend fun getUserAppStage(): AppStage {
        return withContext(dispatcher.io()) {
            val userStage = userStageDao.currentUserAppStage()
            return@withContext userStage?.appStage ?: AppStage.NEW
        }
    }

    override suspend fun stageCompleted(appStage: AppStage): AppStage {
        return withContext(dispatcher.io()) {
            val newAppStage = when (appStage) {
                AppStage.NEW -> AppStage.DAX_ONBOARDING
                AppStage.DAX_ONBOARDING -> AppStage.ESTABLISHED
                AppStage.USE_OUR_APP_NOTIFICATION -> AppStage.ESTABLISHED
                AppStage.USE_OUR_APP_ONBOARDING -> AppStage.ESTABLISHED
                AppStage.ESTABLISHED -> AppStage.ESTABLISHED
            }

            if (newAppStage != appStage) {
                userStageDao.updateUserStage(newAppStage)
            }

            return@withContext newAppStage
        }
    }

    override suspend fun moveToStage(appStage: AppStage) {
        userStageDao.updateUserStage(appStage)
    }

    private suspend fun moveUserToEstablished3DaysAfterInstall() {
        if (variantManager.getVariant().hasFeature(VariantManager.VariantFeature.KillOnboarding)) {
            if (appInstallStore.hasInstallTimestampRecorded() && daxOnboardingActive()) {
                val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - appInstallStore.installTimestamp)
                if (days >= 3) {
                    moveToStage(AppStage.ESTABLISHED)
                }
            }
        }
    }
}

suspend fun UserStageStore.isNewUser(): Boolean {
    return this.getUserAppStage() == AppStage.NEW
}

suspend fun UserStageStore.daxOnboardingActive(): Boolean {
    return this.getUserAppStage() == AppStage.DAX_ONBOARDING
}

suspend fun UserStageStore.isEstablished(): Boolean {
    return this.getUserAppStage() == AppStage.ESTABLISHED
}

suspend fun UserStageStore.useOurAppOnboarding(): Boolean {
    return this.getUserAppStage() == AppStage.USE_OUR_APP_ONBOARDING
}

suspend fun UserStageStore.useOurAppNotification(): Boolean {
    return this.getUserAppStage() == AppStage.USE_OUR_APP_NOTIFICATION
}
