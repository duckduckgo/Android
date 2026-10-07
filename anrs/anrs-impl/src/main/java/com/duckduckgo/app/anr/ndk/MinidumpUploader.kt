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
package com.duckduckgo.app.anr.ndk

import android.content.Context
import androidx.core.content.edit
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Named

class MinidumpUploader @Inject constructor(
    private val context: Context,
    private val nativeCrashFeature: NativeCrashFeature,
    @param:Named("api") private val okHttpClient: OkHttpClient,
    private val timeProvider: CurrentTimeProvider,
    sharedPreferencesProvider: SharedPreferencesProvider,
) {

    private val prefs by lazy { sharedPreferencesProvider.getSharedPreferences(PREFS_NAME) }

    fun uploadPending(crashMetadata: String) {
        val url = uploadUrl()
        if (url.isEmpty()) return
        val dumps = pendingMinidumps()
        if (dumps.isEmpty()) return
        if (!isThrottled()) {
            prefs.edit { putLong(KEY_LAST_UPLOAD_ATTEMPT, timeProvider.currentTimeMillis()) }
            upload(url, dumps.first(), crashMetadata)
        }
        dumps.forEach(::delete)
    }

    private fun isThrottled(): Boolean {
        val sinceLastAttempt = timeProvider.currentTimeMillis() - prefs.getLong(KEY_LAST_UPLOAD_ATTEMPT, 0)
        return sinceLastAttempt in 0 until UPLOAD_INTERVAL_MILLIS
    }

    internal fun uploadUrl(): String {
        val toggle = nativeCrashFeature.uploadMinidumps()
        if (!toggle.isEnabled()) return ""
        return runCatching {
            val settings = toggle.getSettings() ?: return@runCatching ""
            JSONObject(settings).optString(SETTINGS_KEY_UPLOAD_URL)
        }.getOrDefault("")
    }

    private fun pendingMinidumps(): List<File> =
        context.filesDir.resolve("crashpad/pending")
            .listFiles { f -> f.extension == "dmp" }
            ?.sortedBy { it.lastModified() }
            ?: emptyList()

    private fun upload(url: String, dump: File, crashMetadata: String) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("upload_file_minidump", dump.name, dump.asRequestBody("application/octet-stream".toMediaType()))
            .addFormDataPart("crash_metadata", crashMetadata)
            .build()
        val request = Request.Builder().url(url).post(body).build()
        runCatching { okHttpClient.newCall(request).execute().close() }
            .onFailure { logcat(ERROR) { "ndk-crash: minidump upload failed: ${it.asLog()}" } }
    }

    private fun delete(dump: File) {
        dump.delete()
        dump.resolveSibling("${dump.nameWithoutExtension}.meta").delete()
        dump.resolveSibling("${dump.nameWithoutExtension}.lock").delete()
    }

    companion object {
        private const val SETTINGS_KEY_UPLOAD_URL = "uploadUrl"
        private const val PREFS_NAME = "com.duckduckgo.app.anr.minidump.upload"
        private const val KEY_LAST_UPLOAD_ATTEMPT = "last_upload_attempt"
        private const val UPLOAD_INTERVAL_MILLIS = 60 * 60 * 1000L
    }
}
