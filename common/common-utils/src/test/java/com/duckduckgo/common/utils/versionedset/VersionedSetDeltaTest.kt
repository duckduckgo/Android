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

package com.duckduckgo.common.utils.versionedset

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionedSetDeltaTest {

    @Test
    fun whenNewerReplacementThenReturnsReplacementUpdate() {
        val delta = VersionedSetDelta(insert = setOf("new"), delete = setOf("old"), revision = 2, replace = true)

        assertEquals(VersionedSetUpdate.Replace(setOf("new"), 2), delta.updateFrom(currentRevision = 1))
    }

    @Test
    fun whenNewerDeltaThenReturnsDeltaUpdate() {
        val delta = VersionedSetDelta(insert = setOf("new"), delete = setOf("old"), revision = 2, replace = false)

        assertEquals(VersionedSetUpdate.Delta(setOf("new"), setOf("old"), 2), delta.updateFrom(currentRevision = 1))
    }

    @Test
    fun whenRevisionIsCurrentThenReturnsIgnore() {
        val delta = VersionedSetDelta(insert = setOf("new"), delete = emptySet(), revision = 1, replace = false)

        assertEquals(VersionedSetUpdate.Ignore, delta.updateFrom(currentRevision = 1))
    }

    @Test
    fun whenRevisionIsOlderThenReturnsIgnore() {
        val delta = VersionedSetDelta(insert = setOf("new"), delete = emptySet(), revision = 1, replace = false)

        assertEquals(VersionedSetUpdate.Ignore, delta.updateFrom(currentRevision = 2))
    }
}
