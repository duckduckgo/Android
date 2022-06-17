/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.vpn.internal.feature.transparency

import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.RequestTrackerType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations

class TransparencyTrackerDetectorInterceptorTest {

    private lateinit var testee: TransparencyTrackerDetectorInterceptor

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        testee = TransparencyTrackerDetectorInterceptor()
    }

    @Test
    fun returnsNotTrackerWhenTransparencyEnabled() {
        testee.setEnable(true)
        val trackerType = testee.interceptTrackerRequest(HOSTNAME, SOME_PACKAGE_ID)
        assertEquals(trackerType, RequestTrackerType.NotTracker(HOSTNAME))
    }

    @Test
    fun returnsNullWhenTransparencyDisabled() {
        testee.setEnable(false)
        val trackerType = testee.interceptTrackerRequest(HOSTNAME, SOME_PACKAGE_ID)
        assertNull(trackerType)
    }

    companion object {
        private const val HOSTNAME = "hostname"
        private const val SOME_PACKAGE_ID = "some.package.name"
    }

}
