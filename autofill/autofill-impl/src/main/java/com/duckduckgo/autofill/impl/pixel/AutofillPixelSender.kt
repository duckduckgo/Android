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

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_DEVICE_CAPABILITY_CAPABLE
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_DEVICE_CAPABILITY_DEVICE_AUTH_DISABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_DEVICE_CAPABILITY_SECURE_STORAGE_UNAVAILABLE
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_DEVICE_CAPABILITY_SECURE_STORAGE_UNAVAILABLE_AND_DEVICE_AUTH_DISABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_DEVICE_CAPABILITY_UNKNOWN_ERROR
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.VERBOSE
import logcat.logcat

interface AutofillPixelSender {
    suspend fun hasDeterminedCapabilities(): Boolean
    fun sendCapabilitiesPixel(
        secureStorageAvailable: Boolean,
        deviceAuthAvailable: Boolean,
    )

    fun sendCapabilitiesUndeterminablePixel()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class AutofillUniquePixelSender @Inject constructor(
    private val pixel: Pixel,
    private val context: Context,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : AutofillPixelSender {

    val preferences: SharedPreferences
        get() = context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)

    override suspend fun hasDeterminedCapabilities(): Boolean {
        return preferences.getBoolean(KEY_CAPABILITIES_DETERMINED, false)
    }

    override fun sendCapabilitiesPixel(
        secureStorageAvailable: Boolean,
        deviceAuthAvailable: Boolean,
    ) {
        appCoroutineScope.launch(dispatchers.io()) {
            sendPixel(secureStorageAvailable, deviceAuthAvailable).let {
                logcat(VERBOSE) { "Autofill capability pixel fired: $it" }
            }
            preferences.edit { putBoolean(KEY_CAPABILITIES_DETERMINED, true) }
        }
    }

    override fun sendCapabilitiesUndeterminablePixel() {
        appCoroutineScope.launch(dispatchers.io()) {
            pixel.fire(AUTOFILL_DEVICE_CAPABILITY_UNKNOWN_ERROR)
            preferences.edit { putBoolean(KEY_CAPABILITIES_DETERMINED, true) }
        }
    }

    private fun sendPixel(
        secureStorageAvailable: Boolean,
        deviceAuthAvailable: Boolean,
    ): AutofillPixelNames {
        val pixelName = if (secureStorageAvailable && deviceAuthAvailable) {
            AUTOFILL_DEVICE_CAPABILITY_CAPABLE
        } else if (!secureStorageAvailable && !deviceAuthAvailable) {
            AUTOFILL_DEVICE_CAPABILITY_SECURE_STORAGE_UNAVAILABLE_AND_DEVICE_AUTH_DISABLED
        } else if (!deviceAuthAvailable) {
            AUTOFILL_DEVICE_CAPABILITY_DEVICE_AUTH_DISABLED
        } else {
            AUTOFILL_DEVICE_CAPABILITY_SECURE_STORAGE_UNAVAILABLE
        }
        pixel.fire(pixelName)
        return pixelName
    }

    companion object {
        private const val SHARED_PREFS_FILE = "com.duckduckgo.autofill.pixel.AutofillPixelSender"
        private const val KEY_CAPABILITIES_DETERMINED = "KEY_CAPABILITIES_DETERMINED"
    }
}
