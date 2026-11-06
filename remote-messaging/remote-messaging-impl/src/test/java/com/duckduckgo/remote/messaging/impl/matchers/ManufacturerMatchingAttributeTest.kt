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

package com.duckduckgo.remote.messaging.impl.matchers

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.DefaultBrowser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ManufacturerMatchingAttributeMapperTest {

    private val testee = ManufacturerMatchingAttributeMapper()

    @Test
    fun whenKeyIsManufacturerAndValueIsAStringListThenReturnsAttributeWithThatList() {
        val result = testee.map("manufacturer", JsonMatchingAttribute(value = listOf("samsung", "Google")))

        assertEquals(ManufacturerMatchingAttribute(listOf("samsung", "Google")), result)
    }

    @Test
    fun whenKeyIsManufacturerAndValueIsAListWithNonStringsThenKeepsOnlyStrings() {
        val result = testee.map("manufacturer", JsonMatchingAttribute(value = listOf("samsung", 1, null)))

        assertEquals(ManufacturerMatchingAttribute(listOf("samsung")), result)
    }

    @Test
    fun whenKeyIsManufacturerAndValueIsABareStringThenReturnsAttributeWithEmptyList() {
        val result = testee.map("manufacturer", JsonMatchingAttribute(value = "samsung"))

        assertEquals(ManufacturerMatchingAttribute(emptyList()), result)
    }

    @Test
    fun whenKeyIsManufacturerAndValueIsNullThenReturnsAttributeWithEmptyList() {
        val result = testee.map("manufacturer", JsonMatchingAttribute(value = null))

        assertEquals(ManufacturerMatchingAttribute(emptyList()), result)
    }

    @Test
    fun whenKeyIsNotManufacturerThenReturnsNull() {
        val result = testee.map("locale", JsonMatchingAttribute(value = listOf("samsung")))

        assertNull(result)
    }
}

class ManufacturerAttributeMatcherPluginTest {

    private val appBuildConfig: AppBuildConfig = mock()

    private val testee = ManufacturerAttributeMatcherPlugin(appBuildConfig)

    @Test
    fun whenManufacturerIsInListThenReturnsTrue() = runTest {
        whenever(appBuildConfig.manufacturer).thenReturn("samsung")

        assertEquals(true, testee.evaluate(ManufacturerMatchingAttribute(listOf("samsung"))))
    }

    @Test
    fun whenManufacturerMatchesIgnoringCaseThenReturnsTrue() = runTest {
        whenever(appBuildConfig.manufacturer).thenReturn("samsung")

        assertEquals(true, testee.evaluate(ManufacturerMatchingAttribute(listOf("Samsung"))))
    }

    @Test
    fun whenManufacturerIsNotInListThenReturnsFalse() = runTest {
        whenever(appBuildConfig.manufacturer).thenReturn("Google")

        assertEquals(false, testee.evaluate(ManufacturerMatchingAttribute(listOf("samsung", "xiaomi"))))
    }

    @Test
    fun whenListIsEmptyThenReturnsFalse() = runTest {
        whenever(appBuildConfig.manufacturer).thenReturn("samsung")

        assertEquals(false, testee.evaluate(ManufacturerMatchingAttribute(emptyList())))
    }

    @Test
    fun whenAttributeIsDifferentThenReturnsNullWithoutQueryingAppBuildConfig() = runTest {
        assertNull(testee.evaluate(DefaultBrowser(value = true)))

        verify(appBuildConfig, never()).manufacturer
    }
}
