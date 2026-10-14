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

package com.duckduckgo.app.browser.applinks

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.BrowserTabViewModel
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.AppLink
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DuckDuckGoAppLinksLauncherTest {

    private val mockContext: Context = mock()
    private val mockViewModel: BrowserTabViewModel = mock()
    private val testee = DuckDuckGoAppLinksLauncher()

    @Before
    fun setup() {
        whenever(mockContext.packageName).thenReturn("com.duckduckgo.test")
    }

    @Test
    fun whenAppLinkLaunchesSuccessfullyThenReturnsTrue() {
        val appLink = AppLink(uriString = "example.com", appIntent = Intent())
        assertTrue(testee.openAppLink(mockContext, appLink, mockViewModel))
        verify(mockViewModel).clearPreviousUrl()
    }

    @Test
    fun whenStartActivityThrowsActivityNotFoundThenReturnsFalse() {
        doThrow(ActivityNotFoundException()).whenever(mockContext).startActivity(any())
        val appLink = AppLink(uriString = "example.com", appIntent = Intent())
        assertFalse(testee.openAppLink(mockContext, appLink, mockViewModel))
        verify(mockViewModel).clearPreviousUrl()
    }

    @Test
    fun whenAppIntentIsNullThenReturnsFalseAndDoesNotStartActivity() {
        val appLink = AppLink(uriString = "example.com", appIntent = null)
        assertFalse(testee.openAppLink(mockContext, appLink, mockViewModel))
        verify(mockContext, never()).startActivity(any())
        verify(mockViewModel).clearPreviousUrl()
    }

    @Test
    fun whenContextIsNullThenReturnsFalse() {
        val appLink = AppLink(uriString = "example.com", appIntent = Intent())
        assertFalse(testee.openAppLink(null, appLink, mockViewModel))
        verifyNoInteractions(mockViewModel)
    }
}
