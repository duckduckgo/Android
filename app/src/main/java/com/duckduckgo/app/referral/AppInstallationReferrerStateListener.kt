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

package com.duckduckgo.app.referral

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse.*
import com.android.installreferrer.api.InstallReferrerStateListener
import com.duckduckgo.app.playstore.PlayStoreAndroidUtils.Companion.PLAY_STORE_PACKAGE
import com.duckduckgo.app.playstore.PlayStoreAndroidUtils.Companion.PLAY_STORE_REFERRAL_SERVICE
import com.duckduckgo.app.referral.ParseFailureReason.*
import com.duckduckgo.app.referral.ParsedReferrerResult.ParseFailure
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject

class AppInstallationReferrerStateListener @Inject constructor(
    context: Context,
    private val packageManager: PackageManager,
    private val appInstallationReferrerParser: AppInstallationReferrerParser
) : InstallReferrerStateListener {

    private val referralClient = InstallReferrerClient.newBuilder(context).build()

    private var referralCodeContinuation: CancellableContinuation<ParsedReferrerResult>? = null

    suspend fun retrieveReferralCode(timeoutMs: Long): ParsedReferrerResult {
        Timber.i("Referrer: Retrieving referral code from Play Store referrer service")

        return suspendCoroutineWithTimeout(timeoutMs) { continuation ->
            referralCodeContinuation = continuation

            if (playStoreReferralServiceInstalled()) {
                referralClient.startConnection(this)
            } else {
                val wrappedError = ParseFailure(ReferralServiceUnavailable)
                resumeContinuation(wrappedError)
            }
        }
    }

    private fun playStoreReferralServiceInstalled(): Boolean {
        val playStoreConnectionServiceIntent = Intent()
        playStoreConnectionServiceIntent.component = ComponentName(PLAY_STORE_PACKAGE, PLAY_STORE_REFERRAL_SERVICE)
        val matchingServices = packageManager.queryIntentServices(playStoreConnectionServiceIntent, 0)
        return matchingServices.size > 0
    }

    override fun onInstallReferrerSetupFinished(responseCode: Int) {
        if (referralCodeContinuation?.isCancelled == true) {
            Timber.d("Already canceled; doing nothing")
            return
        }

        when (responseCode) {
            OK -> {
                Timber.i("Successfully connected to Referrer service")

                val response = referralClient.installReferrer
                val referrer = response.installReferrer
                val parsedResult = appInstallationReferrerParser.parse(referrer)
                resumeContinuation(parsedResult)
            }
            FEATURE_NOT_SUPPORTED -> resumeContinuation(ParseFailure(FeatureNotSupported))
            SERVICE_UNAVAILABLE -> resumeContinuation(ServiceUnavailable)
            DEVELOPER_ERROR -> resumeContinuation(DeveloperError)
            SERVICE_DISCONNECTED -> resumeContinuation(ServiceDisconnected)
            else -> resumeContinuation(UnknownError)
        }
        referralClient.endConnection()
    }

    override fun onInstallReferrerServiceDisconnected() {
        Timber.i("Referrer: ServiceDisconnected")
    }

    private fun resumeContinuation(result: ParsedReferrerResult) {
        referralCodeContinuation?.resumeWith(Result.success(result))
    }

    private fun resumeContinuation(errorReason: ParseFailureReason) {
        referralCodeContinuation?.resumeWith(Result.success(ParseFailure(errorReason)))
    }

    private suspend inline fun <T> suspendCoroutineWithTimeout(timeoutMs: Long, crossinline block: (CancellableContinuation<T>) -> Unit): T {
        return withTimeout(timeoutMs) {
            suspendCancellableCoroutine(block = block)
        }
    }

}