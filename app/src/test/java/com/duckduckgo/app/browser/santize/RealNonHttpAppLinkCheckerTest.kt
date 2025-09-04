/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.santize

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealNonHttpAppLinkCheckerTest {

    @Mock
    private val appBuildConfig: AppBuildConfig = mock()

    private lateinit var testee: RealNonHttpAppLinkChecker

    @Before
    fun setUp() {
        whenever(appBuildConfig.applicationId).thenReturn("com.duckduckgo.mobile.android")
        testee = RealNonHttpAppLinkChecker(appBuildConfig)
    }

    @Test
    fun whenIntentDataStringContainsInternalContentFileProviderThenReturnFalse() {
        val intent = Intent().apply {
            data = Uri.parse("content://com.duckduckgo.mobile.android.provider/path/to/file")
        }

        val result = testee.isPermitted(intent)

        assertFalse(result)
    }

    @Test
    fun whenIntentDataStringContainsInternalContentFileProviderWithSubpathThenReturnFalse() {
        val intent = Intent().apply {
            data = Uri.parse("content://com.duckduckgo.mobile.android.provider/external_files/documents/test.pdf")
        }

        val result = testee.isPermitted(intent)

        assertFalse(result)
    }

    @Test
    fun whenIntentDataStringContainsExternalContentProviderThenReturnTrue() {
        val intent = Intent().apply {
            data = Uri.parse("content://com.other.app.provider/path/to/file")
        }

        val result = testee.isPermitted(intent)

        assertTrue(result)
    }

    @Test
    fun whenIntentDataStringIsHttpsUrlThenReturnTrue() {
        val intent = Intent().apply {
            data = Uri.parse("https://example.com/page")
        }

        val result = testee.isPermitted(intent)

        assertTrue(result)
    }

    @Test
    fun whenIntentDataStringIsCustomSchemeThenReturnTrue() {
        val intent = Intent().apply {
            data = Uri.parse("myapp://action/data")
        }

        val result = testee.isPermitted(intent)

        assertTrue(result)
    }

    @Test
    fun whenIntentDataIsNullThenReturnTrue() {
        val intent = Intent().apply {
            data = null
        }

        val result = testee.isPermitted(intent)

        assertTrue(result)
    }

    @Test
    fun whenIntentDataStringIsNullThenReturnTrue() {
        val intent = Intent().apply {
            data = Uri.parse("")
        }

        val result = testee.isPermitted(intent)

        assertTrue(result)
    }

    @Test
    fun whenIntentDataStringContainsInternalProviderInUpperCaseThenReturnFalse() {
        val intent = Intent().apply {
            data = Uri.parse("CONTENT://COM.DUCKDUCKGO.MOBILE.ANDROID.PROVIDER/file")
        }

        assertFalse(testee.isPermitted(intent))
    }

    @Test
    fun whenIntentClipDataContainsInternalContentFileProviderThenReturnFalse() {
        val clipData = ClipData.newRawUri("test", Uri.parse("content://com.duckduckgo.mobile.android.provider/file"))
        val intent = Intent().apply {
            data = Uri.parse("https://example.com/safe")
            setClipData(clipData)
        }

        val result = testee.isPermitted(intent)

        assertFalse(result)
    }

    @Test
    fun whenIntentClipDataContainsMultipleUrisWithInternalProviderThenReturnFalse() {
        val clipData = ClipData.newRawUri("test", Uri.parse("https://example.com/safe"))
        clipData.addItem(ClipData.Item(Uri.parse("content://com.duckduckgo.mobile.android.provider/file")))
        val intent = Intent().apply {
            data = Uri.parse("https://example.com/safe")
            setClipData(clipData)
        }

        val result = testee.isPermitted(intent)

        assertFalse(result)
    }

    @Test
    fun whenIntentClipDataContainsOnlyExternalProvidersThenReturnTrue() {
        val clipData = ClipData.newRawUri("test", Uri.parse("content://com.other.app.provider/file"))
        clipData.addItem(ClipData.Item(Uri.parse("https://example.com/safe")))
        val intent = Intent().apply {
            data = Uri.parse("https://example.com/safe")
            setClipData(clipData)
        }

        val result = testee.isPermitted(intent)

        assertTrue(result)
    }

    @Test
    fun whenIntentClipDataIsNullThenReturnTrue() {
        val intent = Intent().apply {
            data = Uri.parse("https://example.com/safe")
            clipData = null
        }

        val result = testee.isPermitted(intent)

        assertTrue(result)
    }

    @Test
    fun whenIntentClipDataHasItemWithNullUriThenReturnTrue() {
        val clipData = ClipData.newPlainText("test", "some text")
        val intent = Intent().apply {
            data = Uri.parse("https://example.com/safe")
            setClipData(clipData)
        }

        val result = testee.isPermitted(intent)

        assertTrue(result)
    }
}
