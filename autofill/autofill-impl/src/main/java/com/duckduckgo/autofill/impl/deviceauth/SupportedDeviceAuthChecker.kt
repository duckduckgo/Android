/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.deviceauth

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface SupportedDeviceAuthChecker {
    fun supportsStrongAuthentication(): Boolean
    fun supportsLegacyAuthentication(): Boolean
}

@ContributesBinding(AppScope::class)
class RealSupportedDeviceAuthChecker @Inject constructor(
    private val context: Context,
) : SupportedDeviceAuthChecker {
    private val biometricManager: BiometricManager by lazy {
        BiometricManager.from(context)
    }

    private val keyguardManager: KeyguardManager? by lazy {
        runCatching { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager? }.getOrNull()
    }

    override fun supportsStrongAuthentication(): Boolean =
        biometricManager.canAuthenticate(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL) == BIOMETRIC_SUCCESS

    override fun supportsLegacyAuthentication(): Boolean {
        return keyguardManager?.isDeviceSecure ?: false
    }
}
