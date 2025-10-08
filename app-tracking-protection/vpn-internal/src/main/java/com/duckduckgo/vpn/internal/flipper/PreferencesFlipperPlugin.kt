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

package com.duckduckgo.vpn.internal.flipper

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.core.content.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.facebook.flipper.core.FlipperConnection
import com.facebook.flipper.core.FlipperObject
import com.facebook.flipper.core.FlipperPlugin
import com.facebook.flipper.core.FlipperReceiver
import com.frybits.harmony.getHarmonySharedPreferences
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val XML_SUFFIX = ".xml"
private const val SHARED_PREFS_DIR = "shared_prefs"
private const val HARMONY_PREFS_DIR = "harmony_prefs"

@ContributesMultibinding(AppScope::class)
class PreferencesFlipperPlugin @Inject constructor(
    context: Context,
    @AppCoroutineScope coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : FlipperPlugin {

    private var connection: FlipperConnection? = null

    private val onSharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        // We assume it could happen sharedPreferences are not initialised here, but not anywhere else in this file
        val descriptor = runCatching { this.sharedPreferences[sharedPreferences] }.getOrNull() ?: return@OnSharedPreferenceChangeListener

        connection?.send(
            "sharedPreferencesChange",
            FlipperObject.Builder()
                .put("preferences", descriptor.name)
                .put("name", key)
                .put("deleted", !sharedPreferences.contains(key))
                .put("time", System.currentTimeMillis())
                .put("value", sharedPreferences.all[key])
                .build(),
        )
    }

    private lateinit var sharedPreferences: Map<SharedPreferences, SharedPreferencesDescriptor>

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            val descriptors = buildDescriptorForAllPrefsFiles(context)
            val prefs = HashMap<SharedPreferences, SharedPreferencesDescriptor>(descriptors.size)
            descriptors.forEach { descriptor ->
                descriptor.getSharedPreferences(context).run {
                    registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
                    prefs[this] = descriptor
                }
            }
            sharedPreferences = prefs
        }
    }

    override fun getId(): String = "Preferences"

    override fun onConnect(connection: FlipperConnection?) {
        this.connection = connection

        connection!!.receive(
            "getAllSharedPreferences",
        ) { _, responder ->
            val builder = FlipperObject.Builder()
            for ((key, value) in sharedPreferences.entries) {
                builder.put(value.name, key.asFlipperObject())
            }
            responder.success(builder.build())
        }

        connection.receive(
            "getSharedPreferences",
            FlipperReceiver { params, responder ->
                val name = params.getString("name")
                if (name != null) {
                    responder.success(getFlipperObjectFor(name))
                }
            },
        )

        connection.receive(
            "setSharedPreference",
            FlipperReceiver { params, responder ->
                val sharedPreferencesName = params.getString("sharedPreferencesName")
                val preferenceName = params.getString("preferenceName")
                val sharedPrefs = getSharedPreferencesFor(sharedPreferencesName)
                val originalValue = sharedPrefs.all[preferenceName]
                sharedPrefs.edit {
                    when (originalValue) {
                        is Boolean -> {
                            putBoolean(preferenceName, params.getBoolean("preferenceValue"))
                        }
                        is Long -> {
                            putLong(preferenceName, params.getLong("preferenceValue"))
                        }
                        is Int -> {
                            putInt(preferenceName, params.getInt("preferenceValue"))
                        }
                        is Float -> {
                            putFloat(preferenceName, params.getFloat("preferenceValue"))
                        }
                        is String -> {
                            putString(preferenceName, params.getString("preferenceValue"))
                        }
                        else -> {
                            throw IllegalArgumentException("Type not supported: $preferenceName")
                        }
                    }
                }
                responder.success(getFlipperObjectFor(sharedPreferencesName))
            },
        )

        connection.receive(
            "deleteSharedPreference",
            FlipperReceiver { params, responder ->
                val sharedPreferencesName = params.getString("sharedPreferencesName")
                val preferenceName = params.getString("preferenceName")
                getSharedPreferencesFor(sharedPreferencesName).edit {
                    remove(preferenceName)
                }
                responder.success(getFlipperObjectFor(sharedPreferencesName))
            },
        )
    }

    override fun onDisconnect() {
        this.connection = null
    }

    override fun runInBackground(): Boolean = false

    private data class SharedPreferencesDescriptor(
        val name: String,
        val mode: Int,
    ) {
        fun getSharedPreferences(context: Context): SharedPreferences {
            return if (mode == Context.MODE_MULTI_PROCESS) {
                context.getHarmonySharedPreferences(name)
            } else {
                context.getSharedPreferences(name, mode)
            }
        }
    }

    private fun getFlipperObjectFor(name: String): FlipperObject {
        return getSharedPreferencesFor(name).asFlipperObject()
    }

    private fun SharedPreferences.asFlipperObject(): FlipperObject {
        return FlipperObject.Builder().apply {
            all.forEach { entry ->
                entry.key?.let {
                    put(it, entry.value)
                }
            }
        }.build()
    }

    private fun getSharedPreferencesFor(name: String): SharedPreferences {
        return sharedPreferences.entries.first { it.value.name == name }.key
    }

    companion object {
        private fun buildDescriptorForAllPrefsFiles(context: Context): List<SharedPreferencesDescriptor> {
            // shared prefs
            val dir = File(context.applicationInfo.dataDir, SHARED_PREFS_DIR)
            val list = dir.list { _, name -> name.endsWith(XML_SUFFIX) }
            val descriptors: MutableList<SharedPreferencesDescriptor> = ArrayList()
            if (list != null) {
                for (each in list) {
                    val prefName = each.substring(0, each.indexOf(XML_SUFFIX))
                    descriptors.add(SharedPreferencesDescriptor(prefName, Context.MODE_PRIVATE))
                }
            }

            // default shared prefs
            descriptors.add(SharedPreferencesDescriptor(getDefaultSharedPreferencesName(context), Context.MODE_PRIVATE))

            // Harmony prefs go at the end to ensure they'll override the shared prefs
            context.harmonyPrefsFolder().list()?.forEach { prefName ->
                descriptors.add(SharedPreferencesDescriptor(prefName, Context.MODE_MULTI_PROCESS))
            }

            return descriptors
        }

        private fun getDefaultSharedPreferencesName(context: Context): String {
            return PreferenceManager.getDefaultSharedPreferencesName(context)
        }
    }
}

private fun Context.harmonyPrefsFolder(): File {
    return File(filesDir, HARMONY_PREFS_DIR).apply {
        if (!exists()) mkdirs()
    }
}
