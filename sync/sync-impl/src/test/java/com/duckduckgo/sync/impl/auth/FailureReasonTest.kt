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

import androidx.biometric.BiometricPrompt
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.FailureReason
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class FailureReasonTest {

    @Test
    fun `when converting a known biometric error code then return its failure reason`(
        @TestParameter(valuesProvider = KnownFailureReasonsProvider::class) failureReason: FailureReason,
    ) {
        assertEquals(failureReason, FailureReason.fromBiometricErrorCode(BIOMETRIC_ERROR_CODES.getValue(failureReason)))
    }

    @Test
    fun `when converting an unknown biometric error code then return other`() {
        assertEquals(FailureReason.Other, FailureReason.fromBiometricErrorCode(999))
    }

    private class KnownFailureReasonsProvider : TestParameterValuesProvider() {
        // We take every entry but Other, so a new entry without a code makes the test above fail.
        override fun provideValues(context: Context) = FailureReason.entries - FailureReason.Other
    }

    private companion object {
        val BIOMETRIC_ERROR_CODES = mapOf(
            FailureReason.NegativeButton to BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            FailureReason.SystemCanceled to BiometricPrompt.ERROR_CANCELED,
            FailureReason.Lockout to BiometricPrompt.ERROR_LOCKOUT,
            FailureReason.LockoutPermanent to BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
            FailureReason.NoBiometrics to BiometricPrompt.ERROR_NO_BIOMETRICS,
            FailureReason.NoDeviceCredential to BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
            FailureReason.HwUnavailable to BiometricPrompt.ERROR_HW_UNAVAILABLE,
            FailureReason.HwNotPresent to BiometricPrompt.ERROR_HW_NOT_PRESENT,
            FailureReason.UnableToProcess to BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
            FailureReason.Timeout to BiometricPrompt.ERROR_TIMEOUT,
            FailureReason.NoSpace to BiometricPrompt.ERROR_NO_SPACE,
            FailureReason.Vendor to BiometricPrompt.ERROR_VENDOR,
            FailureReason.SecurityUpdateRequired to BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
        )
    }
}
