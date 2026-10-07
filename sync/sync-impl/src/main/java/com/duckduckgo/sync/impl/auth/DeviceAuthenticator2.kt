/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.auth

import androidx.annotation.StringRes
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.SyncBuildConfig
import com.duckduckgo.sync.impl.auth.AuthPrompt.Enroll
import com.duckduckgo.sync.impl.auth.AuthPrompt.Verify
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator2.Event
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator2.Request
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator2.Response
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Decides whether and how the user has to authenticate.
 */
interface DeviceAuthenticator2 {
    /**
     * The prompt the UI should show, or null when there is nothing to show. Render it with [AuthPromptRenderer].
     */
    val currentPrompt: StateFlow<AuthPrompt?>

    /**
     * Checks whether the user has to authenticate and, when they do, publishes a prompt on [currentPrompt] and suspends until
     * it is answered.
     *
     * Calls are serialized, so at most one prompt is pending at a time. Cancelling the call clears [currentPrompt], which
     * dismisses the shown prompt.
     *
     * @param onEvent reports what is shown to the user while the call is in progress
     */
    suspend fun authenticate(
        request: Request,
        onEvent: (Event) -> Unit = {},
    ): Response

    data class Request(
        @StringRes val verifyPromptTitle: Int = R.string.sync_biometric_prompt_title,
        @StringRes val verifyPromptMessage: Int = R.string.sync_auth_text_for_access,
        @StringRes val enrollPromptTitle: Int = R.string.sync_simplified_settings_require_passcode_dialog_title,
        @StringRes val enrollPromptMessage: Int = R.string.sync_simplified_settings_require_passcode_dialog_body,
        @StringRes val enrollPromptCta: Int = R.string.sync_simplified_settings_require_passcode_dialog_primary_button,
    )

    sealed interface Event {
        /**
         * The device has no screen lock, so [authenticate] is about to show [AuthPrompt.Enroll].
         */
        data object EnrollmentNeeded : Event

        /**
         * [AuthPrompt.Enroll] is on screen.
         */
        data object EnrollmentShown : Event

        /**
         * [AuthPrompt.Verify] is on screen.
         */
        data object VerificationShown : Event
    }

    sealed interface Response {
        /**
         * The user may proceed.
         *
         * @property isAuthenticated false only when the device has no screen lock and the build doesn't require
         * authentication, so the user proceeds without having authenticated. This never happens in production: only UI tests
         * that skip device authentication turn the requirement off, through [SyncBuildConfig.isAuthRequired].
         */
        data class Allowed(val isAuthenticated: Boolean) : Response

        /**
         * The user dismissed the verification prompt.
         */
        data object Cancelled : Response

        /**
         * Verification ended with an error.
         */
        data class Failed(val reason: String) : Response

        /**
         * The device has no screen lock and the user closed the enrollment prompt. The user may have set up a screen lock in
         * the meantime, so the caller can authenticate again.
         */
        data object EnrollmentClosed : Response
    }
}

@ContributesBinding(AppScope::class, boundType = DeviceAuthenticator2::class)
class RealDeviceAuthenticator2 @Inject constructor(
    private val deviceAuthChecker: SupportedDeviceAuthChecker,
    private val gracePeriod: DeviceAuthorizationGracePeriod,
    private val buildConfig: SyncBuildConfig,
) : DeviceAuthenticator2 {
    private val mutex = Mutex()
    private val _currentPrompt = MutableStateFlow<AuthPrompt?>(null)

    override val currentPrompt: StateFlow<AuthPrompt?> = _currentPrompt.asStateFlow()

    override suspend fun authenticate(
        request: Request,
        onEvent: (Event) -> Unit,
    ): Response {
        return mutex.withLock {
            val hasValidDeviceAuthentication = hasValidDeviceAuthentication()
            when {
                !buildConfig.isAuthRequired && !hasValidDeviceAuthentication -> {
                    Response.Allowed(isAuthenticated = false)
                }

                !hasValidDeviceAuthentication -> {
                    showEnrollment(request, onEvent)
                }

                gracePeriod.isAuthRequired() -> {
                    showVerification(request, onEvent)
                }

                else -> {
                    Response.Allowed(isAuthenticated = true)
                }
            }
        }
    }

    private suspend fun showEnrollment(
        request: Request,
        onEvent: (Event) -> Unit,
    ): Response {
        onEvent(Event.EnrollmentNeeded)
        showPrompt { continuation ->
            EnrollPrompt(
                title = request.enrollPromptTitle,
                message = request.enrollPromptMessage,
                cta = request.enrollPromptCta,
                notifyShown = { onEvent(Event.EnrollmentShown) },
                continuation = continuation,
            )
        }
        return Response.EnrollmentClosed
    }

    private suspend fun showVerification(
        request: Request,
        onEvent: (Event) -> Unit,
    ): Response {
        val result = showPrompt { continuation ->
            VerifyPrompt(
                title = request.verifyPromptTitle,
                message = request.verifyPromptMessage,
                notifyShown = { onEvent(Event.VerificationShown) },
                continuation = continuation,
            )
        }
        return when (result) {
            is VerifyResponse.Verified -> {
                gracePeriod.recordSuccessfulAuthorization()
                Response.Allowed(isAuthenticated = true)
            }

            is VerifyResponse.Cancelled -> {
                Response.Cancelled
            }

            is VerifyResponse.Error -> {
                Response.Failed(result.reason)
            }
        }
    }

    private suspend fun <R> showPrompt(create: (CancellableContinuation<R>) -> AuthPrompt): R {
        return try {
            suspendCancellableCoroutine { continuation ->
                _currentPrompt.value = create(continuation)
            }
        } finally {
            _currentPrompt.value = null
        }
    }

    private fun hasValidDeviceAuthentication(): Boolean {
        // https://developer.android.com/reference/androidx/biometric/BiometricManager#canAuthenticate(int)
        // BIOMETRIC_STRONG | DEVICE_CREDENTIAL is unsupported on API 28-29
        return if (buildConfig.sdkInt !in setOf(28, 29)) {
            deviceAuthChecker.supportsStrongAuthentication()
        } else {
            deviceAuthChecker.supportsLegacyAuthentication()
        }
    }

    private class VerifyPrompt(
        override val title: Int,
        override val message: Int,
        notifyShown: () -> Unit,
        private val continuation: CancellableContinuation<VerifyResponse>,
    ) : ReportShownOnce(notifyShown), Verify {
        override fun onVerified() = resume(VerifyResponse.Verified)

        override fun onCancelled() = resume(VerifyResponse.Cancelled)

        override fun onError(reason: String) = resume(VerifyResponse.Error(reason))

        private fun resume(result: VerifyResponse) {
            if (continuation.isActive) continuation.resume(result)
        }
    }

    private class EnrollPrompt(
        override val title: Int,
        override val message: Int,
        override val cta: Int,
        notifyShown: () -> Unit,
        private val continuation: CancellableContinuation<Unit>,
    ) : ReportShownOnce(notifyShown), Enroll {
        override fun onClosed() {
            if (continuation.isActive) continuation.resume(Unit)
        }
    }

    // A recreated activity shows the same prompt again, so we report only the first time it appears.
    private abstract class ReportShownOnce(private val notifyShown: () -> Unit) {
        private var reported = false

        fun onShown() {
            if (reported) return
            reported = true
            notifyShown()
        }
    }

    private sealed interface VerifyResponse {
        data object Verified : VerifyResponse
        data object Cancelled : VerifyResponse
        data class Error(val reason: String) : VerifyResponse
    }
}
