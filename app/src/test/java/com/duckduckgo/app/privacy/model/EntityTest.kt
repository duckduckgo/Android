/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.privacy.model

import com.duckduckgo.app.trackerdetection.model.TdsEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityTest {

    @Test
    fun whenEntityPrevalenceIsGreaterThan7ThenIsMajorIsTrue() {
        assertTrue(TdsEntity("", "", 7.1).isMajor)
    }

    @Test
    fun whenEntityPrevalenceIs7ThenIsMajorIsFalse() {
        assertFalse(TdsEntity("", "", 7.0).isMajor)
    }
}
