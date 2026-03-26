/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.vpn.internal.feature.rules

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.registerExportedReceiver
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerExceptionRule
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * This receiver allows to add exclusion rules to appTP
 *
 * $ adb shell am broadcast -a rule --es app <package.id> --es domain <the.tracker.domain>
 *
 * where `--es app <id>` is the app package ID
 * where `--es domain <domain>` is the tracker domain
 */
class ExceptionRulesDebugReceiver(
    context: Context,
    intentAction: String = ACTION,
    private val receiver: (Intent) -> Unit,
) : BroadcastReceiver() {

    init {
        kotlin.runCatching { context.unregisterReceiver(this) }
        // needs to be exported because it's register on the :vpn process but can be intent-trigger from the :app process
        context.registerExportedReceiver(this, IntentFilter(intentAction))
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        receiver(intent)
    }

    companion object {
        private const val ACTION = "rule"

        fun ruleIntent(
            packageId: String,
            domain: String,
        ): Intent {
            return Intent(ACTION).apply {
                putExtra("app", packageId)
                putExtra("domain", domain)
            }
        }
    }
}

@ContributesMultibinding(VpnScope::class)
class ExceptionRulesDebugReceiverRegister @Inject constructor(
    private val context: Context,
    private val exclusionRulesRepository: ExclusionRulesRepository,
    private val dispatchers: DispatcherProvider,
) : VpnServiceCallbacks {

    private val exceptionRulesSavedState = mutableListOf<AppTrackerExceptionRule>()
    private val shouldSaveRules = AtomicBoolean(true)

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        logcat { "Debug receiver ExceptionRulesDebugReceiver registered" }

        ExceptionRulesDebugReceiver(context) { intent ->
            val appId = kotlin.runCatching { intent.getStringExtra("app") }.getOrNull()
            val domain = kotlin.runCatching { intent.getStringExtra("domain") }.getOrNull()

            logcat { "Excluding $domain for app $appId" }

            if (appId != null && domain != null) {
                coroutineScope.launch(dispatchers.io()) {
                    // first save the current state, just once
                    saveExceptionRulesState()
                    exclusionRulesRepository.upsertRule(appId, domain)
                }
            }
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        if (shouldSaveRules.get()) {
            // We haven't saved any rules yet - noop
            logcat { "Debug receiver ExceptionRulesDebugReceiver will not restore rules. Rules size = ${exceptionRulesSavedState.size} " }
            return
        }

        logcat { "Debug receiver ExceptionRulesDebugReceiver restoring exception rules of size = ${exceptionRulesSavedState.size}" }

        coroutineScope.launch(dispatchers.io()) {
            exclusionRulesRepository.deleteAllTrackerRules()
            exclusionRulesRepository.insertTrackerRules(exceptionRulesSavedState).also {
                exceptionRulesSavedState.clear()
            }
            shouldSaveRules.set(true)
        }
    }

    private suspend fun saveExceptionRulesState() {
        if (shouldSaveRules.compareAndSet(true, false)) {
            exceptionRulesSavedState.clear()
            exceptionRulesSavedState.addAll(exclusionRulesRepository.getAllTrackerRules())
        }
    }
}
