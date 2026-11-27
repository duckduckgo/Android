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

import android.content.Context
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DecodeSemPlatformIntTest {

    @Test
    fun whenRawIs100000ThenDecodesTo1_0() {
        assertEquals(OneUiVersion(1, 0), decodeSemPlatformInt(100000))
    }

    @Test
    fun whenRawIs110500ThenDecodesTo2_5() {
        assertEquals(OneUiVersion(2, 5), decodeSemPlatformInt(110500))
    }

    @Test
    fun whenRawIs170000ThenDecodesTo8_0() {
        assertEquals(OneUiVersion(8, 0), decodeSemPlatformInt(170000))
    }

    @Test
    fun whenRawIs170100ThenDecodesTo8_1() {
        assertEquals(OneUiVersion(8, 1), decodeSemPlatformInt(170100))
    }

    @Test
    fun whenRawIs89999ThenDecodesToNull() {
        assertNull(decodeSemPlatformInt(89999))
    }

    @Test
    fun whenRawIs95000ThenDecodesToNull() {
        assertNull(decodeSemPlatformInt(95000))
    }

    @Test
    fun whenRawIs0ThenDecodesToNull() {
        assertNull(decodeSemPlatformInt(0))
    }

    @Test
    fun whenRawIsIntMaxValueThenDecodesToNonNullVersion() {
        assertNotNull(decodeSemPlatformInt(Int.MAX_VALUE))
    }
}

class RealOneUiVersionProviderTest {

    private val packageManager: PackageManager = mock()
    private val context: Context = mock<Context>().also {
        whenever(it.packageManager).thenReturn(packageManager)
    }

    private val testee = RealOneUiVersionProvider(context)

    @Test
    fun whenNeitherSamsungFeatureIsPresentThenReturnsNull() {
        whenever(packageManager.hasSystemFeature(SEM_FEATURE)).thenReturn(false)
        whenever(packageManager.hasSystemFeature(SEM_FEATURE_LITE)).thenReturn(false)

        assertNull(testee.get())
    }

    @Test
    fun whenSamsungFeatureIsPresentButSemPlatformIntFieldIsAbsentThenReturnsNull() {
        whenever(packageManager.hasSystemFeature(SEM_FEATURE)).thenReturn(true)

        assertNull(testee.get())
    }

    @Test
    fun whenLiteFeatureIsPresentThenProviderAttemptsDetection() {
        whenever(packageManager.hasSystemFeature(SEM_FEATURE)).thenReturn(false)
        whenever(packageManager.hasSystemFeature(SEM_FEATURE_LITE)).thenReturn(true)

        assertNull(testee.get())
        verify(packageManager).hasSystemFeature(SEM_FEATURE_LITE)
    }

    @Test
    fun whenGetIsCalledTwiceThenResultIsCached() {
        whenever(packageManager.hasSystemFeature(SEM_FEATURE)).thenReturn(true)

        testee.get()
        testee.get()

        verify(packageManager, times(1)).hasSystemFeature(SEM_FEATURE)
    }
}
