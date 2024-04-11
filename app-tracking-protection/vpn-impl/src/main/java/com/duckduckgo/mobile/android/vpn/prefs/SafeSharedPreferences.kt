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

package com.duckduckgo.mobile.android.vpn.prefs

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.content.SharedPreferences.OnSharedPreferenceChangeListener

/**
 * This class is a wrapper around shared prefs.
 *
 * We should primarily use it to wrap encrypted shared prefs so that we don't end up with crashes when decrypting information.
 * We know this crashes happen (eg. java.lang.SecurityException: Could not decrypt value.) when eg. user perform backups across devices
 * and the keys are not carried over to the new device.
 */
internal class SafeSharedPreferences(
    private val unsafePrefs: SharedPreferences,
) : SharedPreferences {

    override fun getAll(): MutableMap<String, *> {
        return runCatching { unsafePrefs.all }.getOrDefault(mutableMapOf())
    }

    override fun getString(
        key: String?,
        defValue: String?,
    ): String? {
        return runCatching { unsafePrefs.getString(key, defValue) }.getOrDefault(defValue)
    }

    override fun getStringSet(
        key: String?,
        defValue: MutableSet<String>?,
    ): MutableSet<String>? {
        return runCatching { unsafePrefs.getStringSet(key, defValue) }.getOrDefault(defValue)
    }

    override fun getInt(
        key: String?,
        defValue: Int,
    ): Int {
        return runCatching { unsafePrefs.getInt(key, defValue) }.getOrDefault(defValue)
    }

    override fun getLong(
        key: String?,
        defValue: Long,
    ): Long {
        return runCatching { unsafePrefs.getLong(key, defValue) }.getOrDefault(defValue)
    }

    override fun getFloat(
        key: String?,
        defValue: Float,
    ): Float {
        return runCatching { unsafePrefs.getFloat(key, defValue) }.getOrDefault(defValue)
    }

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ): Boolean {
        return runCatching { unsafePrefs.getBoolean(key, defValue) }.getOrDefault(defValue)
    }

    override fun contains(key: String?): Boolean {
        return runCatching { unsafePrefs.contains(key) }.getOrDefault(false)
    }

    override fun edit(): Editor {
        return SafeEditor(unsafePrefs.edit())
    }

    override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener?) {
        unsafePrefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener?) {
        unsafePrefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private class SafeEditor(
        private val editor: Editor,
    ) : Editor {
        override fun putString(
            key: String?,
            defValue: String?,
        ): Editor {
            runCatching { editor.putString(key, defValue) }
            return this
        }

        override fun putStringSet(
            key: String?,
            defValue: MutableSet<String>?,
        ): Editor {
            runCatching { editor.putStringSet(key, defValue) }
            return this
        }

        override fun putInt(
            key: String?,
            defValue: Int,
        ): Editor {
            runCatching { editor.putInt(key, defValue) }
            return this
        }

        override fun putLong(
            key: String?,
            defValue: Long,
        ): Editor {
            runCatching { editor.putLong(key, defValue) }
            return this
        }

        override fun putFloat(
            key: String?,
            defValue: Float,
        ): Editor {
            runCatching { editor.putFloat(key, defValue) }
            return this
        }

        override fun putBoolean(
            key: String?,
            defValue: Boolean,
        ): Editor {
            runCatching { editor.putBoolean(key, defValue) }
            return this
        }

        override fun remove(key: String?): Editor {
            runCatching { editor.remove(key) }
            return this
        }

        override fun clear(): Editor {
            runCatching { editor.clear() }
            return this
        }

        override fun commit(): Boolean {
            return runCatching { editor.commit() }.getOrDefault(true)
        }

        override fun apply() {
            runCatching { editor.apply() }
        }
    }
}
