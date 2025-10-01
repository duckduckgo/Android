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

package com.duckduckgo.autofill.impl.pixel

import androidx.annotation.UiThread
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.securestorage.SecureStorage
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class AutofillDeviceCapabilityReporter @Inject constructor(
    private val pixel: AutofillPixelSender,
    private val secureStorage: SecureStorage,
    private val dispatchers: DispatcherProvider,
    private val deviceAuthenticator: DeviceAuthenticator,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver {

    @UiThread
    override fun onCreate(owner: LifecycleOwner) {
        logcat(VERBOSE) { "Autofill device capability reporter created" }

        appCoroutineScope.launch(dispatchers.io()) {
            if (pixel.hasDeterminedCapabilities()) {
                logcat(VERBOSE) { "Already determined device autofill capabilities previously" }
                return@launch
            }

            try {
                val secureStorageAvailable = secureStorage.canAccessSecureStorage()
                val deviceAuthAvailable = deviceAuthenticator.hasValidDeviceAuthentication()

                logcat {
                    "Autofill device capabilities:" +
                        "\nSecure storage available: $secureStorageAvailable" +
                        "\nDevice auth available: $deviceAuthAvailable"
                }

                pixel.sendCapabilitiesPixel(secureStorageAvailable, deviceAuthAvailable)
            } catch (e: Error) {
                logcat(WARN) { "Failed to determine device autofill capabilities: ${e.asLog()}" }
                pixel.sendCapabilitiesUndeterminablePixel()
            }
        }
    }
}
