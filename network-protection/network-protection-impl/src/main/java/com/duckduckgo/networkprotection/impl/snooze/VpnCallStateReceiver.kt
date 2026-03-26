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

package com.duckduckgo.networkprotection.impl.snooze

import android.annotation.SuppressLint
import android.content.*
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.ProcessName
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.registerExportedReceiver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ReceiverScope
import com.duckduckgo.mobile.android.vpn.Vpn
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.*
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import javax.inject.Inject
import kotlin.properties.Delegates

@InjectWith(ReceiverScope::class)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class VpnCallStateReceiver @Inject constructor(
    private val context: Context,
    private val vpn: Vpn,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    @ProcessName private val processName: String,
    private val vpnDisableOnCall: VpnDisableOnCall,
    private val networkProtectionState: NetworkProtectionState,
) : BroadcastReceiver(), MainProcessLifecycleObserver {

    private val telephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
    }

    private val _listener: PhoneStateListener =
        object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(
                state: Int,
                phoneNumber: String?,
            ) {
                appCoroutineScope.launch(dispatcherProvider.io()) {
                    logcat { "Call state: $state" }
                    if (networkProtectionState.isEnabled()) {
                        if (state == TelephonyManager.CALL_STATE_IDLE) {
                            vpn.start()
                        } else {
                            vpn.pause()
                        }
                    }
                }
            }
        }
    private var currentListener by Delegates.observable<PhoneStateListener?>(null) { _, old, new ->
        logcat { "CALL_STATE listener registered" }
        old?.let {
            telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        new?.let {
            telephonyManager?.listen(new, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        logcat { "onReceive ${intent.action} in $processName" }
        val pendingResult = goAsync()

        when (intent.action) {
            ACTION_REGISTER_STATE_CALL_LISTENER -> {
                logcat { "ACTION_REGISTER_STATE_CALL_LISTENER" }
                goAsync(pendingResult) {
                    registerListener()
                }
            }

            ACTION_UNREGISTER_STATE_CALL_LISTENER -> {
                logcat { "ACTION_UNREGISTER_STATE_CALL_LISTENER" }
                goAsync(pendingResult) {
                    unregisterListener()
                }
            }

            else -> {
                logcat { "Unknown action ${intent.action}" }
            }
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        register()
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (vpnDisableOnCall.isEnabled()) {
                registerListener()
            } else {
                logcat { "CALL_STATE listener feature is disabled" }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun register() {
        unregister()
        logcat { "Registering vpn call state receiver" }
        context.registerExportedReceiver(
            this,
            IntentFilter().apply {
                addAction(ACTION_REGISTER_STATE_CALL_LISTENER)
                addAction(ACTION_UNREGISTER_STATE_CALL_LISTENER)
            },
        )
    }

    private fun unregister() {
        kotlin.runCatching { context.unregisterReceiver(this) }
    }

    private fun registerListener() {
        runCatching {
            currentListener = _listener
        }.onFailure { t ->
            logcat(ERROR) { "CALL_STATE error registering: ${t.asLog()}" }
        }
    }

    private fun unregisterListener() {
        currentListener = null
    }

    companion object {
        internal const val ACTION_REGISTER_STATE_CALL_LISTENER = "com.duckduckgo.netp.feature.snooze.ACTION_REGISTER_STATE_CALL_LISTENER"
        internal const val ACTION_UNREGISTER_STATE_CALL_LISTENER = "com.duckduckgo.netp.feature.snooze.ACTION_UNREGISTER_STATE_CALL_LISTENER"
    }
}

@Suppress("NoHardcodedCoroutineDispatcher")
private fun goAsync(
    pendingResult: BroadcastReceiver.PendingResult?,
    coroutineScope: CoroutineScope = GlobalScope,
    block: suspend () -> Unit,
) {
    coroutineScope.launch(Dispatchers.IO) {
        try {
            block()
        } finally {
            // Always call finish(), even if the coroutineScope was cancelled
            pendingResult?.finish()
        }
    }
}
