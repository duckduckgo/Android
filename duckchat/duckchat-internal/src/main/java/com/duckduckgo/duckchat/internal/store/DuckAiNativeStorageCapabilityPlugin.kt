/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.internal.store

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.internal.DuckAiDevCapabilityPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import java.net.NetworkInterface
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class DuckAiNativeStorageCapabilityPlugin @Inject constructor(
    private val server: DuckAiDebugServer,
) : DuckAiDevCapabilityPlugin {

    override fun title(): String = "Native Storage Debug"

    override fun subtitle(): String = if (server.isRunning) {
        "Running — http://127.0.0.1:${server.port}"
    } else {
        "Tap to start debug server"
    }

    override fun onCapabilityClicked(activityContext: Context) {
        if (server.isRunning) {
            server.stop()
            Toast.makeText(activityContext, "Debug server stopped", Toast.LENGTH_SHORT).show()
        } else {
            server.start()
            val wifiIp = if (isEmulator()) null else localWifiIp()
            val networkSection = if (wifiIp != null) {
                "From same WiFi network:\nhttp://$wifiIp:${server.port}/debug\n\n" +
                    "From your computer (via USB):\n" +
                    "adb forward tcp:${server.port} tcp:${server.port}\n" +
                    "http://127.0.0.1:${server.port}/debug"
            } else {
                "To access from your computer:\n" +
                    "adb forward tcp:${server.port} tcp:${server.port}\n" +
                    "http://127.0.0.1:${server.port}/debug"
            }
            AlertDialog.Builder(activityContext)
                .setTitle("Native Storage Debug")
                .setMessage("Server running at:\nhttp://127.0.0.1:${server.port}/debug\n\n$networkSection")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")

    private fun localWifiIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.asSequence()
            ?.filter { !it.isLoopback && it.isUp }
            ?.flatMap { it.inetAddresses.asSequence() }
            ?.firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
            ?.hostAddress
    }.getOrNull()
}
