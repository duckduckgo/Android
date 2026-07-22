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

package com.duckduckgo.pir.impl.common

import android.content.Context
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.pir.impl.common.RealPirBenchmarkConfig.Companion.OVERRIDE_FILE_NAME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class RealPirBenchmarkConfigTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val mockContext: Context = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()

    private lateinit var testee: RealPirBenchmarkConfig

    @Before
    fun setUp() {
        whenever(mockContext.filesDir).thenReturn(temporaryFolder.root)
        whenever(mockAppBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        testee = RealPirBenchmarkConfig(mockContext, mockAppBuildConfig)
    }

    private fun writeOverride(content: String) {
        File(temporaryFolder.root, OVERRIDE_FILE_NAME).writeText(content)
    }

    @Test
    fun whenNotInternalBuildThenReturnsNullEvenIfFilePresent() {
        whenever(mockAppBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        writeOverride("30")

        assertNull(testee.getWebViewCountOverride())
    }

    @Test
    fun whenFileMissingThenReturnsNull() {
        assertNull(testee.getWebViewCountOverride())
    }

    @Test
    fun whenFileBlankThenReturnsNull() {
        writeOverride("   ")

        assertNull(testee.getWebViewCountOverride())
    }

    @Test
    fun whenFileNotAnIntegerThenReturnsNull() {
        writeOverride("abc")

        assertNull(testee.getWebViewCountOverride())
    }

    @Test
    fun whenFileHasValidIntegerThenReturnsIt() {
        writeOverride("30")

        assertEquals(30, testee.getWebViewCountOverride())
    }

    @Test
    fun whenFileHasSurroundingWhitespaceThenParsesIt() {
        writeOverride("\n 40 \n")

        assertEquals(40, testee.getWebViewCountOverride())
    }
}
