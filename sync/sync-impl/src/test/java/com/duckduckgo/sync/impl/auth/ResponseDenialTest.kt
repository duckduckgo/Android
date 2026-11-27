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

import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Denial
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Denial.Reason
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.FailureReason
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Response
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class ResponseDenialTest {

    @Test
    fun `when user is allowed then there is no denial`(
        @TestParameter(valuesProvider = AllowedResponsesProvider::class) allowed: Response.Allowed,
    ) {
        assertNull(allowed.denial)
    }

    @Test
    fun `when user dismisses verification then denial is canceled by the user`() {
        assertEquals(Denial(Reason.Canceled, "user_canceled"), Response.Cancelled.VerificationDismissed.denial)
    }

    @Test
    fun `when user closes enrollment then denial is unavailable with no screen lock`() {
        assertEquals(Denial(Reason.Unavailable, "no_screen_lock"), Response.Cancelled.EnrollmentClosed.denial)
    }

    @Test
    fun `when verification fails with a cancel error then denial is canceled`(
        @TestParameter(valuesProvider = CancelingFailureReasonsProvider::class) failureReason: FailureReason,
    ) {
        assertEquals(Denial(Reason.Canceled, failureReason.value), Response.Failed(failureReason, "reason").denial)
    }

    @Test
    fun `when verification fails with a lockout or missing credential then denial is unavailable`(
        @TestParameter(valuesProvider = UnavailableFailureReasonsProvider::class) failureReason: FailureReason,
    ) {
        assertEquals(Denial(Reason.Unavailable, failureReason.value), Response.Failed(failureReason, "reason").denial)
    }

    @Test
    fun `when verification fails with any other error then denial is error`(
        @TestParameter(valuesProvider = ErrorFailureReasonsProvider::class) failureReason: FailureReason,
    ) {
        assertEquals(Denial(Reason.Error, failureReason.value), Response.Failed(failureReason, "reason").denial)
    }

    private class AllowedResponsesProvider : TestParameterValuesProvider() {
        override fun provideValues(context: Context) = listOf(
            Response.Allowed.UserAuthenticated,
            Response.Allowed.WithinGracePeriod,
            Response.Allowed.NotRequiredForBuild,
        )
    }

    private class CancelingFailureReasonsProvider : TestParameterValuesProvider() {
        override fun provideValues(context: Context) = CANCELING_FAILURE_REASONS.toList()
    }

    private class UnavailableFailureReasonsProvider : TestParameterValuesProvider() {
        override fun provideValues(context: Context) = UNAVAILABLE_FAILURE_REASONS.toList()
    }

    private class ErrorFailureReasonsProvider : TestParameterValuesProvider() {
        override fun provideValues(context: Context) = FailureReason.entries - CANCELING_FAILURE_REASONS - UNAVAILABLE_FAILURE_REASONS
    }

    private companion object {
        val CANCELING_FAILURE_REASONS = setOf(FailureReason.NegativeButton, FailureReason.SystemCanceled)
        val UNAVAILABLE_FAILURE_REASONS = setOf(
            FailureReason.Lockout,
            FailureReason.LockoutPermanent,
            FailureReason.NoBiometrics,
            FailureReason.NoDeviceCredential,
        )
    }
}
