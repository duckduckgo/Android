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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SentLinksTest {

    private val now = Instant.parse("2026-10-21T12:00:00Z")

    private fun link(id: String, to: String = "me", sentAt: Instant = now) =
        SentLink(id = id, to = to, url = "https://example.com/$id", title = "t$id", sentAt = sentAt.toString())

    @Test
    fun whenLinkFitsThenPayloadHoldsOnlyThatLink() {
        assertEquals(listOf(link("a")), link("a").asPayload()!!.links)
    }

    @Test
    fun whenLinkExceedsSettingSizeLimitThenNoPayload() {
        assertNull(link("huge").copy(url = "https://example.com/" + "a".repeat(800)).asPayload())
    }

    @Test
    fun whenFilteringThenOnlyFreshUndeliveredLinksForThisDevice() {
        val payload = SentLinksPayload(
            links = listOf(
                link("mine"),
                link("other", to = "someone-else"),
                link("delivered"),
                link("stale", sentAt = now - Duration.ofDays(8)),
            ),
        )

        assertEquals(listOf("mine"), payload.linksFor("me", delivered = setOf("delivered"), now = now).map { it.id })
    }

    @Test
    fun whenParsingWireFormatThenReadsAllFieldsAndOffsets() {
        val json = """{"v":1,"links":[{"id":"1","to":"me","url":"https://a.b","title":"T","sentAt":"2026-10-21T13:00:00+02:00","extra":true}]}"""

        val payload = parseSentLinks(json)!!

        assertEquals(SentLink(id = "1", to = "me", url = "https://a.b", title = "T", sentAt = "2026-10-21T13:00:00+02:00"), payload.links.single())
        assertEquals(1, payload.linksFor("me", emptySet(), now).size)
    }

    @Test
    fun whenSerialisingThenRoundTripsWithVersion() {
        val json = SentLinksPayload(links = listOf(link("a"))).toJson()

        assertEquals(true, json.startsWith("""{"v":1,"links":[{"id":"a","to":"me""""))
        assertEquals(listOf(link("a")), parseSentLinks(json)!!.links)
    }

    @Test
    fun whenParsingGarbageThenNull() {
        assertNull(parseSentLinks("not json"))
    }
}
