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

package com.duckduckgo.app.global

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.os.Build
import android.os.Process

abstract class MultiProcessApplication : Application() {
    private val shortProcessNameCached: String by lazy { shortProcessName }
    private val isMainProcessCached: Boolean by lazy { isMainProcess }

    final override fun onCreate() {
        super.onCreate()

        // Isolated processes (e.g. AndroidX PDF sandbox) have no permissions of their own:
        // most system services return null and bindService() is forbidden. Our Dagger graph
        // cannot initialise in this environment, so skip all app init and let the isolated
        // service run with just the base Application.
        if (isIsolatedProcess) return

        if (isMainProcessCached) {
            onMainProcessCreate()
        } else {
            onSecondaryProcessCreate(shortProcessNameCached)
        }
    }

    abstract fun onMainProcessCreate()

    open fun onSecondaryProcessCreate(shortProcessName: String) {}
}

inline val Application.shortProcessName: String
    get() = currentProcessName?.substringAfter(delimiter = "$packageName:", missingDelimiterValue = "UNKNOWN") ?: "UNKNOWN"

inline val Application.isMainProcess: Boolean
    get() = packageName == currentProcessName

inline fun Context.runInSecondaryProcessNamed(
    name: String,
    block: () -> Unit,
) {
    if (currentProcessName == "$packageName:$name") {
        block()
    }
}

val Context.isIsolatedProcess: Boolean
    get() {
        if (Build.VERSION.SDK_INT >= 28) {
            // Android P+ has a public API to check if we're in an isolated process
            return Process.isIsolated()
        }
        return try {
            // Older Android versions don't have a public API, but isolated processes have no permissions,
            // so trying to access any system service will throw a SecurityException.
            val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            activityManager.runningAppProcesses
            false
        } catch (_: SecurityException) {
            true
        }
    }

val Context.currentProcessName: String?
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Application.getProcessName()
    } else {
        processNameFromSystemService()
    }

private fun Context.processNameFromSystemService(): String {
    val am = this.getSystemService(Application.ACTIVITY_SERVICE) as ActivityManager?
    return am?.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName.orEmpty()
}
