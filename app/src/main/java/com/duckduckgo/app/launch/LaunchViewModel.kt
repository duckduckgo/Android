/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.launch

import androidx.lifecycle.ViewModel
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.store.isNewUser
import com.duckduckgo.app.referral.AppInstallationReferrerStateListener
import com.duckduckgo.app.referral.AppInstallationReferrerStateListener.Companion.MAX_REFERRER_WAIT_TIME_MS
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Singleton

class LaunchViewModel(
    private val userStageStore: UserStageStore,
    private val appReferrerStateListener: AppInstallationReferrerStateListener
) :
    ViewModel() {

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    sealed class Command {
        object Onboarding : Command()
        data class Home(val replaceExistingSearch: Boolean = false) : Command()
    }

    suspend fun determineViewToShow() {
        waitForReferrerData()

        if (userStageStore.isNewUser()) {
            command.value = Command.Onboarding
        } else {
            command.value = Command.Home()
        }
    }

    private suspend fun waitForReferrerData() {
        val startTime = System.currentTimeMillis()

        withTimeoutOrNull(MAX_REFERRER_WAIT_TIME_MS) {
            Timber.d("Waiting for referrer")
            return@withTimeoutOrNull appReferrerStateListener.waitForReferrerCode()
        }

        Timber.d("Waited ${System.currentTimeMillis() - startTime}ms for referrer")
    }
}

@Module
@ContributesTo(AppObjectGraph::class)
class LaunchViewModelFactoryModule {
    @Provides
    @Singleton
    @IntoSet
    fun provideLaunchViewModelFactory(
        userStageStore: UserStageStore,
        appInstallationReferrerStateListener: AppInstallationReferrerStateListener
    ): ViewModelFactoryPlugin {
        return LaunchViewModelFactory(userStageStore, appInstallationReferrerStateListener)
    }
}

private class LaunchViewModelFactory(
    private val userStageStore: UserStageStore,
    private val appInstallationReferrerStateListener: AppInstallationReferrerStateListener
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(LaunchViewModel::class.java) -> (LaunchViewModel(userStageStore, appInstallationReferrerStateListener) as T)
                else -> null
            }
        }
    }
}
