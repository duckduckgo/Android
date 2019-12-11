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
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.referral.AppInstallationReferrerStateListener
import com.duckduckgo.app.referral.ParsedReferrerResult
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

class LaunchViewModel(
    private val onboardingStore: OnboardingStore,
    private val appReferrerStateListener: AppInstallationReferrerStateListener
) :
    ViewModel() {

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    sealed class Command {
        object Onboarding : Command()
        data class Home(val replaceExistingSearch: Boolean = false) : Command()
    }

    suspend fun determineViewToShow() {
        val startTime = System.currentTimeMillis()
        val referrer = withTimeoutOrNull(MAX_REFERRER_WAIT_TIME_MS) {
            Timber.d("Waiting for referrer")
            return@withTimeoutOrNull appReferrerStateListener.retrieveReferralCode()
        }

        Timber.d("Waiting ${System.currentTimeMillis() - startTime}ms for referrer")

        when (referrer) {
            is ParsedReferrerResult.ReferrerFound -> {
                Timber.i("Referrer information delivered; matching campaign suffix is ${referrer.campaignSuffix}")
            }
            is ParsedReferrerResult.ReferrerNotFound -> {
                Timber.i("Referrer information delivered but no matching campaign suffix")
            }
            is ParsedReferrerResult.ParseFailure -> {
                Timber.w("Failure to parse referrer data ${referrer.reason}")
            }
            null -> Timber.w("Timed out waiting for referrer data")
        }

        if (onboardingStore.shouldShow) {
            command.value = Command.Onboarding
        } else {
            command.value = Command.Home()
        }
    }

    companion object {
        private const val MAX_REFERRER_WAIT_TIME_MS = 1_500L
    }
}
