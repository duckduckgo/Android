/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.global.useourapp

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UseOurAppDetectorTest {

    private lateinit var testee: UseOurAppDetector

    @Before
    fun setup() {
        testee = UseOurAppDetector()
    }

    @Test
    fun whenCheckingIfUrlIsFromUseOurAppDomainThenReturnTrue() {
        assertTrue(testee.isUseOurAppUrl("http://www.facebook.com"))
    }

    @Test
    fun whenCheckingIfMobileUrlIsFromUseOurAppDomainThenReturnTrue() {
        assertTrue(testee.isUseOurAppUrl("http://m.facebook.com"))
    }

    @Test
    fun whenCheckingIfMobileOnlyDomainIsFromUseOurAppDomainThenReturnTrue() {
        assertTrue(testee.isUseOurAppUrl("m.facebook.com"))
    }

    @Test
    fun whenCheckingIfOnlyDomainUrlIsFromUseOurAppDomainThenReturnTrue() {
        assertTrue(testee.isUseOurAppUrl("facebook.com"))
    }

    @Test
    fun whenCheckingIfUrlIsFromUseOurAppDomainThenReturnFalse() {
        assertFalse(testee.isUseOurAppUrl("http://example.com"))
    }

}
