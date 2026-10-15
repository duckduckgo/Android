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
package com.duckduckgo.app.buildconfig.rmf

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.remote.messaging.impl.models.DefaultBrowser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IsReturningUserAttributeMatcherPluginTest {
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val testee = IsReturningUserAttributeMatcherPlugin(mockAppBuildConfig)

    @Test
    fun `when app is a reinstall and remote value true, then returns true`() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)

        val result = testee.evaluate(IsReturningUserMatchingAttribute(remoteValue = true))

        assertEquals(true, result)
    }

    @Test
    fun `when app is not a reinstall and remote value true, then returns false`() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(false)

        val result = testee.evaluate(IsReturningUserMatchingAttribute(remoteValue = true))

        assertEquals(false, result)
    }

    @Test
    fun `when app is a reinstall and remote value false, then returns false`() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(true)

        val result = testee.evaluate(IsReturningUserMatchingAttribute(remoteValue = false))

        assertEquals(false, result)
    }

    @Test
    fun `when app is not a reinstall and remote value false, then returns true`() = runTest {
        whenever(mockAppBuildConfig.isAppReinstall()).thenReturn(false)

        val result = testee.evaluate(IsReturningUserMatchingAttribute(remoteValue = false))

        assertEquals(true, result)
    }

    @Test
    fun `when attribute is different, then returns null without querying app build config`() = runTest {
        val result = testee.evaluate(DefaultBrowser(value = true))

        assertNull(result)
        verify(mockAppBuildConfig, never()).isAppReinstall()
    }
}
