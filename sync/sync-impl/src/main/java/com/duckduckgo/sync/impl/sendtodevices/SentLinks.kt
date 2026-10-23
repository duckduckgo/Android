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

package com.duckduckgo.sync.impl.sendtodevices

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

// Wire format shared with macOS/iOS: one settings key per sender, value is SentLinksPayload as JSON.
const val SENT_LINKS_KEY_PREFIX = "sent_links_from_"

data class SentLinksPayload(
    val v: Int = 1,
    val links: List<SentLink> = emptyList(),
)

data class SentLink(
    val id: String,
    val to: String,
    val url: String,
    val title: String = "",
    val sentAt: String,
)

private val MAX_AGE = Duration.ofDays(7)

// The server rejects settings values over 1024 chars. Encryption adds 40 bytes and base64 grows it by 4/3,
// leaving 728 bytes of JSON; keep a margin.
private const val MAX_PAYLOAD_BYTES = 700
const val MAX_TITLE_CHARS = 100

private val adapter: JsonAdapter<SentLinksPayload> =
    Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(SentLinksPayload::class.java)

fun parseSentLinks(json: String?): SentLinksPayload? =
    json?.let { runCatching { adapter.fromJson(it) }.getOrNull() }

fun SentLinksPayload.toJson(): String = adapter.toJson(this)

fun SentLink.isFresh(now: Instant): Boolean =
    runCatching { OffsetDateTime.parse(sentAt).toInstant() }.getOrNull()?.let { Duration.between(it, now) <= MAX_AGE } ?: false

// Each sender only keeps its latest link: a new send replaces the previous one.
fun SentLink.asPayload(): SentLinksPayload? =
    SentLinksPayload(links = listOf(this)).takeIf { it.toJson().toByteArray().size <= MAX_PAYLOAD_BYTES }

fun SentLinksPayload.linksFor(deviceId: String, delivered: Set<String>, now: Instant): List<SentLink> =
    links.filter { it.to == deviceId && it.id !in delivered && it.isFresh(now) }
