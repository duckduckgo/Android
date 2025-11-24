/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.passkey

import android.webkit.JavascriptInterface
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_AUTH_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_AUTH_STARTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_AUTH_SUCCEEDED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_REGISTRATION_FAILED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_REGISTRATION_STARTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_PASSKEY_REGISTRATION_SUCCEEDED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelParameters.ERROR_PIXEL_KEY
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

interface PasskeyDetectionJavascriptInterface {
    @JavascriptInterface
    fun passkeyAuthenticationStarted()

    @JavascriptInterface
    fun passkeyAuthenticationSucceeded()

    @JavascriptInterface
    fun passkeyAuthenticationFailed(
        errorName: String,
        errorMessage: String,
    )

    @JavascriptInterface
    fun passkeyRegistrationStarted()

    @JavascriptInterface
    fun passkeyRegistrationSucceeded()

    @JavascriptInterface
    fun passkeyRegistrationFailed(
        errorName: String,
        errorMessage: String,
    )

    fun name(): String = JAVASCRIPT_INTERFACE_NAME

    private companion object {
        const val JAVASCRIPT_INTERFACE_NAME = "PasskeyDetection"
    }
}

@ContributesBinding(AppScope::class)
class RealPasskeyDetectionJavascriptInterface @Inject constructor(
    private val pixel: Pixel,
) : PasskeyDetectionJavascriptInterface {

    @JavascriptInterface
    override fun passkeyAuthenticationStarted() {
        logcat(LogPriority.INFO) { "Passkey authentication started" }
        pixel.fire(AUTOFILL_PASSKEY_AUTH_STARTED)
    }

    @JavascriptInterface
    override fun passkeyAuthenticationSucceeded() {
        logcat(LogPriority.INFO) { "Passkey authentication succeeded" }
        pixel.fire(AUTOFILL_PASSKEY_AUTH_SUCCEEDED)
    }

    @JavascriptInterface
    override fun passkeyAuthenticationFailed(
        errorName: String,
        errorMessage: String,
    ) {
        logcat(LogPriority.INFO) { "Passkey authentication failed: $errorName - $errorMessage" }
        pixel.fire(AUTOFILL_PASSKEY_AUTH_FAILED, mapOf(ERROR_PIXEL_KEY to errorName))
    }

    @JavascriptInterface
    override fun passkeyRegistrationStarted() {
        logcat(LogPriority.INFO) { "Passkey registration started" }
        pixel.fire(AUTOFILL_PASSKEY_REGISTRATION_STARTED)
    }

    @JavascriptInterface
    override fun passkeyRegistrationSucceeded() {
        logcat(LogPriority.INFO) { "Passkey registration succeeded" }
        pixel.fire(AUTOFILL_PASSKEY_REGISTRATION_SUCCEEDED)
    }

    @JavascriptInterface
    override fun passkeyRegistrationFailed(
        errorName: String,
        errorMessage: String,
    ) {
        logcat(LogPriority.INFO) { "Passkey registration failed: $errorName - $errorMessage" }
        pixel.fire(AUTOFILL_PASSKEY_REGISTRATION_FAILED, mapOf(ERROR_PIXEL_KEY to errorName))
    }
}
