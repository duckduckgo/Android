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

package com.duckduckgo.vpn.internal.feature

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Abstract class to create generic receivers for internal features accessible through
 * adb commands.
 *
 * Implement the [intentAction] function and return the intent action this receiver will be
 * listening to
 */
abstract class InternalFeatureReceiver(
    private val context: Context,
    private val receiver: (Intent) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        receiver(intent)
    }

    abstract fun intentAction(): String

    fun register() {
        unregister()
        context.registerReceiver(this, IntentFilter(intentAction()))
    }

    fun unregister() {
        kotlin.runCatching { context.unregisterReceiver(this) }
    }
}
