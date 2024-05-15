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

package com.duckduckgo.app.test

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.ProcessName
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.test.MyPlugin
import com.duckduckgo.test.MyPlugin.Companion.MAIN_PLUGIN_PRIORITY
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesActivePluginPoint(
    scope = AppScope::class,
    boundType = MyPlugin::class,
)
private interface MyPluginPointTrigger

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = MyPlugin::class,
    priority = MAIN_PLUGIN_PRIORITY,
)
class MainActivePlugin @Inject constructor() : MyPlugin {
    override fun doSomething() {
        Timber.d("Aitor Main")
    }
}

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = MyPlugin::class,
)
class SecondMainActivePlugin @Inject constructor() : MyPlugin {
    override fun doSomething() {
        Timber.d("Aitor Second Main")
    }
}

@ContributesMultibinding(
    scope =  AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@ContributesMultibinding(
    scope =  AppScope::class,
    boundType = VpnServiceCallbacks::class,
)
class UserOfThePluginPoint @Inject constructor(
    private val pp: ActivePluginPoint<@JvmSuppressWildcards MyPlugin>,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    @ProcessName private val pName: String,
) : MainProcessLifecycleObserver, VpnServiceCallbacks {

    override fun onResume(owner: LifecycleOwner) {
        something(coroutineScope)
    }

    private fun something(coroutineScope: CoroutineScope,) {
        coroutineScope.launch {
            delay(1500) // give time for remote config to update
            pp.getPlugins().forEach {
                Timber.d("In Process $pName")
                it.doSomething()
            }
        }
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        something(coroutineScope)
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason
    ) {}
}
