/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.feature.toggles.internal.api

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import okio.Buffer
import org.json.JSONException
import org.json.JSONObject

/**
 * Shared JSON model classes and a pre-built [Moshi] instance used exclusively by
 * [com.duckduckgo.anvil.annotations.ContributesRemoteFeature]-generated `_RemoteFeature` classes.
 *
 * These types are public because they must be visible to generated code that lands in arbitrary
 * feature modules. They are an internal implementation detail of the remote feature framework
 * and should never be used directly by feature code.
 *
 * They live here (feature-toggles-internal-api) rather than in feature-toggles-api because
 * feature-toggles-api already exposes this module via `api project(":feature-toggles-internal-api")`,
 * so every feature module that depends on feature-toggles-api also gets these on its compile
 * classpath — no new dependency edges are needed.  Keeping them out of feature-toggles-api avoids
 * polluting the public API surface with JSON parsing implementation details.
 *
 * Previously, each of the ~143 usages of @ContributesRemoteFeature generated private copies of
 * these 8 types (≈90 DEX methods per feature ≈ 12,870 methods in total). Sharing a single copy
 * here saves ~12,780 DEX methods across the app.
 */

data class JsonToggleRolloutStep(
    val percent: Double,
)

data class JsonToggleRollout(
    val steps: List<JsonToggleRolloutStep>,
)

data class JsonToggleTarget(
    val variantKey: String,
    val localeCountry: String,
    val localeLanguage: String,
    val isReturningUser: Boolean?,
    val isPrivacyProEligible: Boolean?,
    val entitlement: String?,
    val minSdkVersion: Int?,
)

data class JsonToggleCohort(
    val name: String,
    val weight: Int,
)

data class JsonToggle(
    val state: String?,
    val minSupportedVersion: Double?,
    val rollout: JsonToggleRollout?,
    val targets: List<JsonToggleTarget>,
    val cohorts: List<JsonToggleCohort>,
    val settings: JSONObject?,
    val exceptions: List<JsonException>,
)

data class JsonFeature(
    val state: String?,
    val hash: String?,
    val minSupportedVersion: Int?,
    val settings: JSONObject?,
    val exceptions: List<JsonException>,
    val features: Map<String, JsonToggle>?,
)

data class JsonException(
    val domain: String,
    val reason: String?,
)

class JSONObjectAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): JSONObject? = (reader.readJsonValue() as? Map<*, *>)?.let { data ->
        try {
            JSONObject(data)
        } catch (e: JSONException) {
            return null
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: JSONObject?) {
        value?.let { writer.run { value(Buffer().writeUtf8(value.toString())) } }
    }
}

/**
 * Pre-built [Moshi] instance configured with [JSONObjectAdapter].
 * Used by all @ContributesRemoteFeature-generated `_RemoteFeature` classes for
 * serializing/deserializing [com.duckduckgo.feature.toggles.api.Toggle.State] and
 * parsing remote config JSON payloads.
 */
val REMOTE_FEATURE_MOSHI: Moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
