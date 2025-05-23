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

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.content.pm.PackageManager.DONT_KILL_APP
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.logcat

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class AutofillServiceLifecycleObserver @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context,
    private val autofillServiceFeature: AutofillServiceFeature,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        appCoroutineScope.launch(dispatcherProvider.io()) {
            runCatching {
                val currentState = getAutofillServiceState(context).toBoolean() ?: return@launch

                autofillServiceFeature.self().isEnabled().let { remoteState ->
                    if (currentState != remoteState) {
                        logcat { "DDGAutofillService: Updating state to $remoteState" }
                        newState(context, remoteState)
                    }
                }
            }.onFailure {
                logcat(ERROR) { "DDGAutofillService: Failed to update Service state: $it" }
            }
        }
    }

    private fun getAutofillServiceState(context: Context): Int {
        val pm = context.packageManager
        val autofillServiceComponent = ComponentName(context, RealAutofillService::class.java)
        return pm.getComponentEnabledSetting(autofillServiceComponent)
    }

    private fun newState(
        context: Context,
        isEnabled: Boolean,
    ) {
        val pm = context.packageManager
        val autofillServiceComponent = ComponentName(context, RealAutofillService::class.java)

        val value = when (isEnabled) {
            true -> COMPONENT_ENABLED_STATE_ENABLED
            false -> COMPONENT_ENABLED_STATE_DISABLED
        }

        pm.setComponentEnabledSetting(autofillServiceComponent, value, DONT_KILL_APP)
    }

    private fun Int.toBoolean(): Boolean? {
        return when (this) {
            COMPONENT_ENABLED_STATE_DEFAULT -> false // this is the current value in Manifest
            COMPONENT_ENABLED_STATE_ENABLED -> true
            COMPONENT_ENABLED_STATE_DISABLED -> false
            COMPONENT_ENABLED_STATE_DISABLED_USER -> null
            COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> null
            else -> null
        }
    }
}
