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

package com.duckduckgo.data.store.impl

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.anrs.api.CrashLogger.Crash
import logcat.LogPriority.ERROR
import logcat.LogPriority.WARN
import logcat.logcat
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * This class is a wrapper around shared prefs.
 *
 * We should primarily use it to wrap encrypted shared prefs so that we don't end up with crashes when decrypting information.
 * We know this crashes happen (eg. java.lang.SecurityException: Could not decrypt value.) when eg. user perform backups across devices
 * and the keys are not carried over to the new device.
 */
internal class SafeSharedPreferences(
    private val unsafePrefs: SharedPreferences,
    private val crashLogger: CrashLogger,
    private val crashLoggerExecutor: Executor = Executors.newSingleThreadExecutor { Thread(it, "SafeSharedPrefsCrashLogger") },
) : SharedPreferences {

    override fun getAll(): MutableMap<String, *> =
        runCatching { unsafePrefs.all }
            .getOrElse {
                handleError("getAll", it)
                mutableMapOf<String, Any>()
            }

    override fun getString(key: String?, defValue: String?): String? =
        runCatching { unsafePrefs.getString(key, defValue) }
            .getOrElse {
                handleError(key, it)
                defValue
            }

    override fun getStringSet(key: String?, defValue: MutableSet<String>?): MutableSet<String>? =
        runCatching { unsafePrefs.getStringSet(key, defValue) }
            .getOrElse {
                handleError(key, it)
                defValue
            }

    override fun getInt(key: String?, defValue: Int): Int =
        runCatching { unsafePrefs.getInt(key, defValue) }
            .getOrElse {
                handleError(key, it)
                defValue
            }

    override fun getLong(key: String?, defValue: Long): Long =
        runCatching { unsafePrefs.getLong(key, defValue) }
            .getOrElse {
                handleError(key, it)
                defValue
            }

    override fun getFloat(key: String?, defValue: Float): Float =
        runCatching { unsafePrefs.getFloat(key, defValue) }
            .getOrElse {
                handleError(key, it)
                defValue
            }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        runCatching { unsafePrefs.getBoolean(key, defValue) }
            .getOrElse {
                handleError(key, it)
                defValue
            }

    override fun contains(key: String?): Boolean =
        runCatching { unsafePrefs.contains(key) }
            .getOrElse {
                handleError(key, it)
                false
            }

    override fun edit(): Editor = SafeEditor(unsafePrefs.edit(), crashLogger, crashLoggerExecutor)

    override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener?) =
        unsafePrefs.registerOnSharedPreferenceChangeListener(listener)

    override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener?) =
        unsafePrefs.unregisterOnSharedPreferenceChangeListener(listener)

    private fun handleError(context: String?, t: Throwable) {
        val root = t.cause ?: t
        if (root is android.system.ErrnoException) {
            logcat(WARN) { "fsync failed (EIO) in SharedPreferences at key=$context" }
            crashLoggerExecutor.execute {
                crashLogger.logCrash(Crash("shared-prefs", root))
            }
        } else {
            logcat(ERROR) { "Unexpected SharedPreferences error at key=$context" }
        }
    }

    private class SafeEditor(
        private val editor: Editor,
        private val crashLogger: CrashLogger,
        private val crashLoggerExecutor: Executor,
    ) : Editor {

        private fun handleError(context: String?, t: Throwable) {
            val root = t.cause ?: t
            if (root is android.system.ErrnoException) {
                logcat(ERROR) { "fsync failed (EIO) in SharedPreferences at key=$context" }
                crashLoggerExecutor.execute {
                    crashLogger.logCrash(Crash("shared-prefs", root))
                }
            } else {
                logcat(ERROR) { "Unexpected SharedPreferences error at key=$context" }
            }
        }

        override fun putString(key: String?, value: String?): Editor =
            apply { runCatching { editor.putString(key, value) }.onFailure { handleError(key, it) } }

        override fun putStringSet(key: String?, values: MutableSet<String>?): Editor =
            apply { runCatching { editor.putStringSet(key, values) }.onFailure { handleError(key, it) } }

        override fun putInt(key: String?, value: Int): Editor =
            apply { runCatching { editor.putInt(key, value) }.onFailure { handleError(key, it) } }

        override fun putLong(key: String?, value: Long): Editor =
            apply { runCatching { editor.putLong(key, value) }.onFailure { handleError(key, it) } }

        override fun putFloat(key: String?, value: Float): Editor =
            apply { runCatching { editor.putFloat(key, value) }.onFailure { handleError(key, it) } }

        override fun putBoolean(key: String?, value: Boolean): Editor =
            apply { runCatching { editor.putBoolean(key, value) }.onFailure { handleError(key, it) } }

        override fun remove(key: String?): Editor =
            apply { runCatching { editor.remove(key) }.onFailure { handleError(key, it) } }

        override fun clear(): Editor =
            apply { runCatching { editor.clear() }.onFailure { handleError("clear", it) } }

        override fun commit(): Boolean =
            runCatching { editor.commit() }.onFailure { handleError("commit", it) }.getOrDefault(false)

        override fun apply() {
            runCatching { editor.apply() }.onFailure { handleError("apply", it) }
        }
    }
}
