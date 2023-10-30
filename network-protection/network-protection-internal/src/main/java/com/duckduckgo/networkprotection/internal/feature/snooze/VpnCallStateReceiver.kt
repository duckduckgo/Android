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

package com.duckduckgo.networkprotection.internal.feature.snooze

import android.annotation.SuppressLint
import android.content.*
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.ProcessName
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ReceiverScope
import com.duckduckgo.mobile.android.vpn.Vpn
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlin.properties.Delegates
import kotlinx.coroutines.*
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

interface VpnDisableOnCall {
    fun enable()
    fun disable()

    suspend fun isEnabled(): Boolean
}

@InjectWith(ReceiverScope::class)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = VpnDisableOnCall::class,
)
class VpnCallStateReceiver @Inject constructor(
    private val context: Context,
    private val vpn: Vpn,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val sharedPreferencesProvider: VpnSharedPreferencesProvider,
    @ProcessName private val processName: String,
) : BroadcastReceiver(), MainProcessLifecycleObserver, VpnDisableOnCall {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(PREFS_FILENAME, multiprocess = true, migrate = false)
    }

    private val telephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
    }
    private val _listener: PhoneStateListener =
        object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                appCoroutineScope.launch(dispatcherProvider.io()) {
                    logcat { "Call state: $state" }
                    if (state == TelephonyManager.CALL_STATE_IDLE) {
                        vpn.start()
                    } else {
                        vpn.stop()
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

    override fun enable() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            preferences.edit { putBoolean("enabled", true) }
            context.sendBroadcast(Intent(ACTION_REGISTER_STATE_CALL_LISTENER))
        }
    }

    override fun disable() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            context.sendBroadcast(Intent(ACTION_UNREGISTER_STATE_CALL_LISTENER))
            preferences.edit { putBoolean("enabled", false) }
        }
    }

    override suspend fun isEnabled(): Boolean = withContext(dispatcherProvider.io()) {
        return@withContext preferences.getBoolean("enabled", false)
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        logcat { "onReceive ${intent.action} in $processName" }
        val pendingResult = goAsync()

        when (intent.action) {
            ACTION_REGISTER_STATE_CALL_LISTENER -> {
                logcat { "ACTION_UNREGISTER_STATE_CALL_LISTENER" }
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
            if (isEnabled()) {
                registerListener()
            } else {
                logcat { "CALL_STATE listener feature is disabled" }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun register() {
        unregister()
        logcat { "Registering debug re-keying receiver" }
        context.registerReceiver(
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
            logcat(LogPriority.ERROR) { "CALL_STATE error registering: ${t.asLog()}" }
        }
    }

    private fun unregisterListener() {
        currentListener = null
    }

    companion object {
        private const val ACTION_REGISTER_STATE_CALL_LISTENER = "com.duckduckgo.netp.internal.feature.snooze.ACTION_REGISTER_STATE_CALL_LISTENER"
        private const val ACTION_UNREGISTER_STATE_CALL_LISTENER = "com.duckduckgo.netp.internal.feature.snooze.ACTION_UNREGISTER_STATE_CALL_LISTENER"
        private const val PREFS_FILENAME = "com.duckduckgo.netp.internal.feature.call.listener.v1"
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
