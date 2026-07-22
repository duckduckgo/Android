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

package com.duckduckgo.sync.impl.ui

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.sync.api.SyncActivityFromSetupUrl
import com.duckduckgo.sync.api.SyncActivityWithAnotherDevice
import com.duckduckgo.sync.api.SyncActivityWithEmptyParams
import com.duckduckgo.sync.impl.SyncFeature
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import com.duckduckgo.sync.impl.ui.v2.SyncActivity as SyncActivityV2

class SyncActivityParamMapperTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val syncFeature = FakeFeatureToggleFactory.create(
        SyncFeature::class.java,
        ioDispatcher = coroutineRule.testDispatcher,
    )

    private val testee = SyncActivityParamMapper(
        syncFeature = { syncFeature },
        appScope = coroutineRule.testScope.backgroundScope,
    )

    @Before
    fun setup() {
        testee.onCreate(mock())
    }

    @After
    fun tearDown() {
        testee.onDestroy(mock())
    }

    @Test
    fun `map 'with empty params' activity params`() = coroutineRule.testScope.runTest {
        enableSimplifiedSync(false)
        assertEquals(SyncActivity::class.java, testee.map(SyncActivityWithEmptyParams))

        enableSimplifiedSync(true)
        assertEquals(SyncActivityV2::class.java, testee.map(SyncActivityWithEmptyParams))
    }

    @Test
    fun `map 'with source params' activity params`() = coroutineRule.testScope.runTest {
        enableSimplifiedSync(false)
        assertEquals(SyncActivity::class.java, testee.map(SyncActivityWithSourceParams(source = "source")))

        enableSimplifiedSync(true)
        assertEquals(SyncActivityV2::class.java, testee.map(SyncActivityWithSourceParams(source = "source")))
    }

    @Test
    fun `map 'from setup url' activity params`() = coroutineRule.testScope.runTest {
        enableSimplifiedSync(false)
        assertEquals(SyncActivity::class.java, testee.map(SyncActivityFromSetupUrl(url = "url")))

        enableSimplifiedSync(true)
        assertEquals(SyncActivityV2::class.java, testee.map(SyncActivityFromSetupUrl(url = "url")))
    }

    @Test
    fun `map 'with another device' activity params`() = coroutineRule.testScope.runTest {
        enableSimplifiedSync(false)
        assertEquals(SyncActivity::class.java, testee.map(SyncActivityWithAnotherDevice(source = "source")))

        enableSimplifiedSync(true)
        assertEquals(SyncActivityV2::class.java, testee.map(SyncActivityWithAnotherDevice(source = "source")))
    }

    @Test
    fun `map unknown activity params to null`() = coroutineRule.testScope.runTest {
        enableSimplifiedSync(false)
        assertNull(testee.map(object : GlobalActivityStarter.ActivityParams {}))

        enableSimplifiedSync(true)
        assertNull(testee.map(object : GlobalActivityStarter.ActivityParams {}))
    }

    private fun enableSimplifiedSync(enable: Boolean) {
        syncFeature.useSimplifiedSync().setRawStoredState(State(enable))
    }
}
