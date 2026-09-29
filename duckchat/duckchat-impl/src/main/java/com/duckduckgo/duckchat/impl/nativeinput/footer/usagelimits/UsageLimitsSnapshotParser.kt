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

package com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits

import com.duckduckgo.common.utils.CurrentTimeProvider
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * Lenient parser for the `usageLimits` storage entry. Anything the page might send that native does
 * not understand is ignored. A notice that is missing a required field, has an unknown id
 * or window, or has already reset yields no snapshot, to avoid wrong warnings.
 */
class UsageLimitsSnapshotParser @Inject constructor(
    private val currentTimeProvider: CurrentTimeProvider,
) {

    fun parse(json: String?): UsageLimitsSnapshot? {
        if (json.isNullOrBlank()) return null
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            return null
        }
        val notice = parseNotice(root.optJSONObject(NOTICE)) ?: return null
        return UsageLimitsSnapshot(notice = notice, cta = parseCta(root.optJSONObject(CTA)))
    }

    private fun parseNotice(json: JSONObject?): UsageNotice? {
        if (json == null) return null
        val id = UsageNoticeId.fromJsonId(json.optString(ID).takeIf { json.has(ID) }) ?: return null
        val window = UsageWindow.fromJsonId(json.optString(WINDOW).takeIf { json.has(WINDOW) }) ?: return null
        if (!json.has(PERCENT_USED) || json.isNull(PERCENT_USED)) return null
        val percentUsed = json.optDouble(PERCENT_USED).takeUnless { it.isNaN() }?.toInt()?.coerceIn(0, 100) ?: return null
        val resetsAtMillis = parseInstantMillis(json.optString(RESETS_AT).takeIf { json.has(RESETS_AT) }) ?: return null
        if (resetsAtMillis <= currentTimeProvider.currentTimeMillis()) return null
        val reached = json.optBoolean(REACHED, false)
        return UsageNotice(
            id = id,
            window = window,
            percentUsed = percentUsed,
            resetsAtMillis = resetsAtMillis,
            reached = reached,
            dismissible = json.optBoolean(DISMISSIBLE, !reached),
        )
    }

    private fun parseCta(json: JSONObject?): UsageCta? {
        if (json == null) return null
        val id = UsageCtaId.fromJsonId(json.optString(ID).takeIf { json.has(ID) }) ?: return null
        return UsageCta(
            id = id,
            modelId = json.optNonBlankString(MODEL_ID),
            modelIds = json.optJSONArray(MODEL_IDS).toStringList(),
            byModelId = parseByModelId(json.optJSONObject(BY_MODEL_ID)),
            putEntries = parsePutEntries(json.optJSONArray(PUT_ENTRIES)),
        )
    }

    private fun parseByModelId(json: JSONObject?): Map<String, UsageCtaModelTargets> {
        if (json == null) return emptyMap()
        return json.keys().asSequence().mapNotNull { pickerModelId ->
            val targets = json.optJSONObject(pickerModelId) ?: return@mapNotNull null
            pickerModelId to UsageCtaModelTargets(
                modelId = targets.optNonBlankString(MODEL_ID),
                modelIds = targets.optJSONArray(MODEL_IDS).toStringList(),
            )
        }.toMap()
    }

    private fun parsePutEntries(json: JSONArray?): List<UsageCtaPutEntry> {
        if (json == null) return emptyList()
        return (0 until json.length()).mapNotNull { index ->
            val entry = json.optJSONObject(index) ?: return@mapNotNull null
            val key = entry.optNonBlankString(KEY) ?: return@mapNotNull null
            val value = entry.opt(VALUE) ?: return@mapNotNull null
            UsageCtaPutEntry(key = key, value = value as? String ?: value.toString())
        }
    }

    private fun parseInstantMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun JSONObject.optNonBlankString(name: String): String? =
        if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
    }

    private companion object {
        const val NOTICE = "notice"
        const val CTA = "cta"
        const val ID = "id"
        const val WINDOW = "window"
        const val PERCENT_USED = "percentUsed"
        const val RESETS_AT = "resetsAt"
        const val REACHED = "reached"
        const val DISMISSIBLE = "dismissible"
        const val MODEL_ID = "modelId"
        const val MODEL_IDS = "modelIds"
        const val BY_MODEL_ID = "byModelId"
        const val PUT_ENTRIES = "putEntries"
        const val KEY = "key"
        const val VALUE = "value"
    }
}
