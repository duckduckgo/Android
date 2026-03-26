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

package com.duckduckgo.autofill.impl.service

import android.annotation.SuppressLint
import android.view.autofill.AutofillManager
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_DISABLED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SERVICE_ENABLED
import com.duckduckgo.autofill.impl.service.store.AutofillServiceStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class AutofillServiceStatusLifecycleObserver@Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val autofillManager: AutofillManager?,
    private val autofillServiceStore: AutofillServiceStore,
    private val appBuildConfig: AppBuildConfig,
    private val pixel: Pixel,
) : MainProcessLifecycleObserver {

    @SuppressLint("NewApi")
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        appCoroutineScope.launch(dispatcherProvider.io()) {
            logcat(ERROR) { "Autofill service component observer status" }
            runCatching {
                autofillManager ?: return@runCatching
                val isDefault = autofillManager.hasEnabledAutofillServices()
                var ddgIsServiceComponentName = false
                if (appBuildConfig.sdkInt >= 28) { // This is a fallback logic in case previous check fails
                    ddgIsServiceComponentName = autofillManager.autofillServiceComponentName
                        ?.packageName?.contains("com.duckduckgo.mobile.android") ?: false
                }
                val newDefaultState = isDefault || ddgIsServiceComponentName

                if (autofillServiceStore.isDefaultAutofillProvider() != newDefaultState) {
                    triggerPixel(newDefaultState)
                    autofillServiceStore.updateDefaultAutofillProvider(newDefaultState)
                }
            }.onFailure {
                logcat(ERROR) { "Failed to update autofill service status" }
            }
        }
    }

    private fun triggerPixel(newIsEnabledState: Boolean) {
        val pixelName = if (newIsEnabledState) AUTOFILL_SERVICE_ENABLED else AUTOFILL_SERVICE_DISABLED
        pixel.fire(pixelName)
    }
}
