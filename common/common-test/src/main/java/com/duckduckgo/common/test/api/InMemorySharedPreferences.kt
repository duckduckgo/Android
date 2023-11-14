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

package com.duckduckgo.common.test.api

import android.content.SharedPreferences

class InMemorySharedPreferences : SharedPreferences, SharedPreferences.Editor {
    private val stringSetMap = mutableMapOf<String, MutableSet<String>?>()
    private val stringMap = mutableMapOf<String, String?>()
    private val intMap = mutableMapOf<String, Int>()
    private val longMap = mutableMapOf<String, Long>()
    private val floatMap = mutableMapOf<String, Float>()
    private val booleanMap = mutableMapOf<String, Boolean>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    private val maps = listOf<MutableMap<String, *>>(
        stringSetMap,
        stringMap,
        intMap,
        longMap,
        floatMap,
        booleanMap,
    )

    override fun getAll(): MutableMap<String, *> {
        val map = mutableMapOf<String, Any?>()
        maps.forEach { it.forEach { element -> map[element.key] = element.value } }
        return map
    }

    override fun getString(key: String, defValue: String?): String? {
        return stringMap[key] ?: defValue
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        return stringSetMap[key] ?: defValues
    }

    override fun getInt(key: String, defValue: Int): Int {
        return intMap[key] ?: defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        return longMap[key] ?: defValue
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return floatMap[key] ?: defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return booleanMap[key] ?: defValue
    }

    override fun contains(key: String): Boolean {
        return maps.any { it.contains(key) }
    }

    override fun edit(): SharedPreferences.Editor {
        return this
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners -= listener
    }

    override fun putString(key: String, value: String?): SharedPreferences.Editor {
        stringMap[key] = value
        notifyListeners(key)
        return this
    }

    override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
        stringSetMap[key] = values
        notifyListeners(key)
        return this
    }

    override fun putInt(key: String, value: Int): SharedPreferences.Editor {
        intMap[key] = value
        notifyListeners(key)
        return this
    }

    override fun putLong(key: String, value: Long): SharedPreferences.Editor {
        longMap[key] = value
        notifyListeners(key)
        return this
    }

    override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
        floatMap[key] = value
        notifyListeners(key)
        return this
    }

    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
        booleanMap[key] = value
        notifyListeners(key)
        return this
    }

    override fun remove(key: String): SharedPreferences.Editor {
        maps.forEach { it.remove(key) }
        notifyListeners(key)
        return this
    }

    override fun clear(): SharedPreferences.Editor {
        val keys = maps.flatMap { it.keys }.toSet()
        maps.forEach { it.clear() }
        keys.forEach { notifyListeners(it) }
        return this
    }

    override fun commit(): Boolean {
        return true
    }

    override fun apply() {
    }

    private fun notifyListeners(key: String) {
        listeners.forEach { it.onSharedPreferenceChanged(this, key) }
    }
}
