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

package com.duckduckgo.app.fire

import android.content.Context
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.pixels.AppPixelName.DATA_CLEARED_DAILY
import com.duckduckgo.app.statistics.pixels.Pixel
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RuntimeEnvironment
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class ClearDataPixelTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val mockPixel = mock<Pixel>()

    @Test fun whenDataClearedFirstInDayThenPixelSent() {
        val testee = ClearDataPixelImpl(context, mockPixel)

        testee.onDataCleared()

        verify(mockPixel).enqueueFire(eq(DATA_CLEARED_DAILY.pixelName), any(), any())
    }

    @Test fun whenDataClearedAlreadySentTodayThenDoNotSendPixel() {
        givenOnePixelSent(today())
        val testee = ClearDataPixelImpl(context, mockPixel)

        testee.onDataCleared()

        verifyNoInteractions(mockPixel)
    }

    @Test fun whenLastDataClearedHappenedOnAPreviousDayThenPixelSent() {
        givenOnePixelSent(yesterday())
        val testee = ClearDataPixelImpl(context, mockPixel)

        testee.onDataCleared()

        verify(mockPixel).enqueueFire(eq(DATA_CLEARED_DAILY.pixelName), any(), any())
    }

    private fun givenOnePixelSent(dateTime: LocalDateTime) {
        context.getSharedPreferences(ClearDataPixelImpl.FILENAME, Context.MODE_PRIVATE).apply {
            this.edit(commit = true) {
                putString("${DATA_CLEARED_DAILY.pixelName}_timestamp", dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE))
            }
        }
    }

    private fun today(): LocalDateTime {
        return LocalDateTime.now()
    }

    private fun yesterday(): LocalDateTime {
        return LocalDateTime.now().minusDays(1L)
    }
}
