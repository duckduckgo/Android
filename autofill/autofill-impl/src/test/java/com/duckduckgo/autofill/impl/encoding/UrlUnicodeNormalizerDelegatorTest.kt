/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.encoding

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class UrlUnicodeNormalizerDelegatorTest {

    private val legacy: UrlUnicodeNormalizer = mock()
    private val modern: UrlUnicodeNormalizer = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val testee = UrlUnicodeNormalizerDelegator(legacyNormalizer = legacy, modernNormalizer = modern, appBuildConfig = appBuildConfig)

    @Test
    fun whenSdkOlderThan24AndNormalizeToUnicodeCalledThenLegacyNormalizerUsed() {
        configureOldAndroidVersion()
        testee.normalizeUnicode("")
        verify(legacy).normalizeUnicode(any())
    }

    @Test
    fun whenSdkOlderThan24AndNormalizeToAsciiCalledThenLegacyNormalizerUsed() {
        configureOldAndroidVersion()
        testee.normalizeAscii("")
        verify(legacy).normalizeAscii(any())
    }

    @Test
    fun whenSdkIsModernEnoughThanAndNormalizeToUnicodeCalledThenModernNormalizerUsed() {
        configureModernAndroidVersion()
        testee.normalizeUnicode("")
        verify(modern).normalizeUnicode(any())
    }

    @Test
    fun whenSdkIsModernEnoughThanAndNormalizeToAsciiCalledThenModernNormalizerUsed() {
        configureModernAndroidVersion()
        testee.normalizeAscii("")
        verify(modern).normalizeAscii(any())
    }

    private fun configureOldAndroidVersion() {
        whenever(appBuildConfig.sdkInt).thenReturn(23)
    }

    private fun configureModernAndroidVersion() {
        whenever(appBuildConfig.sdkInt).thenReturn(24)
    }
}
