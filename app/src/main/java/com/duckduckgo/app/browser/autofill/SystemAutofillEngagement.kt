/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.autofill

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

interface SystemAutofillEngagement {
    fun onSystemAutofillEvent()
    fun setIdleReturnTriggered(appLaunchOption: String)
    fun clearIdleReturnTriggered()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealSystemAutofillEngagement @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val autofillFeature: AutofillFeature,
    private val pixel: Pixel,
) : SystemAutofillEngagement {

    @Volatile
    private var idleReturnAppLaunchOption: String? = null

    override fun setIdleReturnTriggered(appLaunchOption: String) {
        idleReturnAppLaunchOption = appLaunchOption
    }

    override fun clearIdleReturnTriggered() {
        idleReturnAppLaunchOption = null
    }

    override fun onSystemAutofillEvent() {
        logcat(VERBOSE) { "System autofill event received" }
        appCoroutineScope.launch(dispatchers.io()) {
            val option = idleReturnAppLaunchOption
            if (option != null) {
                pixel.fire(
                    AUTOFILL_AFTER_IDLE_RETURN_PIXEL,
                    mapOf("appLaunchOption" to option),
                    type = Count,
                )
                idleReturnAppLaunchOption = null
            }

            if (autofillFeature.canDetectSystemAutofillEngagement().isEnabled()) {
                pixel.fire(AutofillPixelNames.AUTOFILL_SYSTEM_AUTOFILL_USED, type = Daily())
            }
        }
    }

    companion object {
        const val AUTOFILL_AFTER_IDLE_RETURN_PIXEL = "m_ntp_after_idle_autofill_after_idle_return"
    }
}
