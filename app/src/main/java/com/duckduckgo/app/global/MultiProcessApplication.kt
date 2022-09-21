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

import android.app.Application
import android.os.Build
import android.os.Process
import android.os.StrictMode
import java.io.BufferedReader
import java.io.FileReader

abstract class MultiProcessApplication : Application() {
    private val shortProcessName: String by lazy {
        ProcessName.currentProcessName?.substring(packageName.length) ?: "UNKNOWN"
    }
    private val isMainProcessCached: Boolean by lazy { isMainProcess }

    final override fun onCreate() {
        super.onCreate()
        if (isMainProcessCached) {
            onMainProcessCreate()
        } else {
            onSecondaryProcessCreate(shortProcessName)
        }
    }

    abstract fun onMainProcessCreate()

    open fun onSecondaryProcessCreate(shortProcessName: String) {}
}

inline val Application.isMainProcess: Boolean
    get() = packageName == ProcessName.currentProcessName

inline fun Application.runInSecondaryProcessNamed(name: String, block: () -> Unit) {
    if (ProcessName.currentProcessName == "$packageName:$name") {
        block()
    }
}

object ProcessName {

    val currentProcessName: String?
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                processNameFromProc(Process.myPid())
            }
        }

    private fun processNameFromProc(pid: Int): String? {
        if (pid <= 0) return null

        return runCatching {
            val reader: BufferedReader
            val savedThreadPolicy = StrictMode.allowThreadDiskReads()

            try {
                reader = BufferedReader(FileReader("/proc/$pid/cmdline"))
            } finally {
                StrictMode.setThreadPolicy(savedThreadPolicy)
            }

            val processName = reader.readLine().trim { it <= ' ' }
            runCatching { reader.close() }
            return@runCatching processName
        }.getOrNull()
    }
}
