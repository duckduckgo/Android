/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.impl.internal

import androidx.core.net.toUri
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.autofill.store.InternalTestUserStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * The logic for this implementation of [InternalTestUserChecker] relies on the fact that
 * a user loads a verification url and goes through auth and completes the process to be
 * an internal test user.
 */
@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealInternalTestUserChecker @Inject constructor(
    private val internalTestUserStore: InternalTestUserStore,
    private val appBuildConfig: AppBuildConfig,
) : InternalTestUserChecker {

    private var verificationErrorDetected = false

    override val isInternalTestUser: Boolean
        get() = appBuildConfig.isInternalBuild() || internalTestUserStore.isVerifiedInternalTestUser

    override fun verifyVerificationErrorReceived(url: String) {
        /**
         * When the page is loaded for [LOGIN_URL_SUCCESS] and the user hasn't completed auth,
         * the page could successfully complete loading BUT will emit an http error [WebViewClient.onReceivedHttpError].
         * This http error will be our signal that the user should NOT be an internal test user.
         */
        if (url.internalTestUrlSuccessUrl()) {
            verificationErrorDetected = true
        }
    }

    override fun verifyVerificationCompleted(url: String) {
        /**
         * This method is processed when [LOGIN_URL_SUCCESS] has been successfully loaded. If no http error
         * has been emitted / [verifyVerificationErrorReceived] not called, This means that the user
         * has completed auth and the user should be set as an internal tester.
         */
        if (url.internalTestUrlSuccessUrl()) {
            internalTestUserStore.isVerifiedInternalTestUser = !verificationErrorDetected
            verificationErrorDetected = false
        }
    }

    private fun String.internalTestUrlSuccessUrl(): Boolean = this.toUri().schemeSpecificPart == LOGIN_URL_SUCCESS

    companion object {
        private const val LOGIN_URL_SUCCESS = "//use-login.duckduckgo.com/patestsucceeded"
    }
}
