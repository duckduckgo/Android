/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.global.events.db

import com.duckduckgo.app.global.events.db.UserEventsPayloadMapper.UserEventPayload
import com.duckduckgo.app.global.events.db.UserEventsPayloadMapper.UserEventPayload.SitePayload
import org.junit.Assert.*
import org.junit.Test

class UserEventsPayloadMapperTest {

    private val testee = UserEventsPayloadMapper()

    @Test
    fun whenAddPayloadToVisitedSiteThenPayloadAdded() {
        val initialUserEvent = UserEventEntity(UserEventKey.FIRST_NON_SERP_VISITED_SITE)

        val userEvent = testee.addPayload(initialUserEvent, SitePayload("https://example.com", "example"))

        val payload = testee.getPayload(userEvent) as SitePayload
        assertEquals("https://example.com", payload.url)
        assertEquals("example", payload.title)
    }

    @Test
    fun whenAddPayloadToNonSiteUserEventThenPayloadIsNotAdded() {
        val initialUserEvent = UserEventEntity(UserEventKey.FIRE_BUTTON_HIGHLIGHTED)

        val userEvent = testee.addPayload(initialUserEvent, SitePayload("https://example.com", "example"))

        assertEquals(initialUserEvent, userEvent)
    }

    @Test
    fun whenInvalidUserEventPayloadToVisitedSiteThenPayloadIsNotAdded() {
        val initialUserEvent = UserEventEntity(UserEventKey.FIRST_NON_SERP_VISITED_SITE)

        val userEvent = testee.addPayload(initialUserEvent, UserEventPayload.Empty)

        assertEquals(initialUserEvent, userEvent)
    }
}
