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

package com.duckduckgo.sync.impl.exchange.v2

import android.util.Base64
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import org.json.JSONObject
import javax.inject.Inject

/**
 * Generates and parses v2 linking codes (the QR/URL exchanged during pairing bootstrap).
 *
 * Wire format per Transport TD (Asana 1214486492252757):
 *   https://duckduckgo.com/sync/pairing/#&code2=<base64(JSON)>
 *   JSON = {"version":"2", "channel_id":"<UUID>", "public_key":"<base64url SPKI DER>"}
 *
 * Spec deviation note: the TD's pseudocode reads literally `base64()` for the outer `code2`
 * value, but the deployed wire format across every client uses URL-safe + no-padding (the
 * URL fragment isn't safe for `+`/`/`/`=`). We follow the deployed convention here. If the
 * TD is later tightened to require strict base64, this needs revisiting.
 */
interface ExchangeV2QrCode {
    fun buildLinkingCode(channelId: String, publicKeyBase64Url: String, version: String = "2"): String
    fun parse(text: String): ExchangeV2CodeParseResult
}

sealed interface ExchangeV2CodeParseResult {
    /** Successfully parsed v2 linking code — drives the Scanner-side flow. */
    data class LinkingV2(
        val channelId: String,
        val publicKey: String,
        val version: String,
    ) : ExchangeV2CodeParseResult

    /** v1 linking code (legacy <code=...>); fall back to v1 stack. */
    data object LinkingV1 : ExchangeV2CodeParseResult

    /** A recovery code — no URL prefix. Caller routes to recovery-handling path. */
    data class RecoveryCode(val rawJson: JSONObject) : ExchangeV2CodeParseResult

    /** Anything we couldn't interpret. */
    data object Unknown : ExchangeV2CodeParseResult
}

@ContributesBinding(AppScope::class)
class RealExchangeV2QrCode @Inject constructor() : ExchangeV2QrCode {

    override fun buildLinkingCode(channelId: String, publicKeyBase64Url: String, version: String): String {
        // Compact JSON, no whitespace — cross-platform clients hash/compare the encoded payload
        // byte-for-byte, so JSON serialisation must be deterministic and minimal.
        val payload = JSONObject().apply {
            put("version", version)
            put("channel_id", channelId)
            put("public_key", publicKeyBase64Url)
        }.toString()
        val encoded = Base64.encodeToString(
            payload.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        return "$URL_PREFIX#&$PARAM_V2=$encoded"
    }

    override fun parse(text: String): ExchangeV2CodeParseResult {
        // Tolerate common paste mishaps: leading/trailing whitespace, and a "Linking code: …"
        // or other prefix before the actual URL. If we find an http(s):// substring, parse
        // from there; otherwise treat the trimmed input as a bare base64url string.
        val trimmed = text.trim()
        val httpIndex = sequenceOf("https://", "http://").mapNotNull { trimmed.indexOf(it).takeIf { i -> i >= 0 } }.minOrNull()
        val candidate = if (httpIndex != null) trimmed.substring(httpIndex) else trimmed
        val b64url = extractFragmentParam(candidate) ?: candidate.takeIf(::looksLikeBase64Url)
            ?: return ExchangeV2CodeParseResult.Unknown

        val decoded = runCatching {
            Base64.decode(b64url, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }.getOrNull() ?: return ExchangeV2CodeParseResult.Unknown

        val json = runCatching { JSONObject(String(decoded, Charsets.UTF_8)) }.getOrNull()
            ?: return ExchangeV2CodeParseResult.Unknown

        // Recovery code: {"recovery": { user_id, secret, ... }}
        if (json.has("recovery")) {
            val recovery = json.optJSONObject("recovery") ?: return ExchangeV2CodeParseResult.Unknown
            if (recovery.has("user_id") && recovery.has("secret")) {
                return ExchangeV2CodeParseResult.RecoveryCode(rawJson = recovery)
            }
        }

        // v2 linking code
        if (json.optString("version") == "2" && json.has("channel_id") && json.has("public_key")) {
            return ExchangeV2CodeParseResult.LinkingV2(
                channelId = json.optString("channel_id"),
                publicKey = json.optString("public_key"),
                version = json.optString("version"),
            )
        }

        // v1 linking code: {"connect": {"device_id": ..., "secret_key": ...}}
        if (json.has("connect")) {
            return ExchangeV2CodeParseResult.LinkingV1
        }

        return ExchangeV2CodeParseResult.Unknown
    }

    /**
     * Pull the `code2` or `code` value from a URL fragment, or return null for non-URL input.
     * Fragment may begin with `&` (v2 uses `#&code2=…`) or be bare (v1 uses `#code=…`).
     */
    private fun extractFragmentParam(text: String): String? {
        if (!text.startsWith("http://") && !text.startsWith("https://")) return null
        val fragmentStart = text.indexOf('#').takeIf { it >= 0 } ?: return null
        var fragment = text.substring(fragmentStart + 1)
        if (fragment.startsWith("&")) fragment = fragment.substring(1)
        for (pair in fragment.split('&')) {
            val eq = pair.indexOf('=')
            if (eq <= 0) continue
            val key = pair.substring(0, eq)
            val value = pair.substring(eq + 1)
            if (key == PARAM_V2 || key == PARAM_V1) return value
        }
        return null
    }

    private fun looksLikeBase64Url(s: String): Boolean =
        s.length >= 8 && s.all { it in BASE64_URL_ALPHABET }

    companion object {
        private const val URL_PREFIX = "https://duckduckgo.com/sync/pairing/"
        private const val PARAM_V2 = "code2"
        private const val PARAM_V1 = "code"
        private val BASE64_URL_ALPHABET = (('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '_', '=')).toSet()
    }
}
