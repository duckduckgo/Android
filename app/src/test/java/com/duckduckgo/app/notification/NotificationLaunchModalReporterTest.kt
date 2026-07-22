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

package com.duckduckgo.app.notification

import android.app.Activity
import android.content.Intent
import android.os.BadParcelableException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.modalcoordinator.api.ModalShownReporter
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class NotificationLaunchModalReporterTest {

    private val modalShownReporter: ModalShownReporter = mock()
    private val activity: Activity = mock()
    private val intent: Intent = mock()

    private lateinit var reporter: NotificationLaunchModalReporter

    @Before
    fun setUp() {
        whenever(activity.intent).thenReturn(intent)
        reporter = NotificationLaunchModalReporter(modalShownReporter)
    }

    @Test
    fun whenLaunchedFromNotificationAndNoSavedInstanceStateThenReportsModalShown() {
        whenever(intent.getBooleanExtra(eq(EXTRA_LAUNCHED_FROM_NOTIFICATION), any())).thenReturn(true)

        reporter.onActivityCreated(activity, savedInstanceState = null)

        verify(modalShownReporter).reportModalShown()
    }

    @Test
    fun whenLaunchedFromNotificationButHasSavedInstanceStateThenDoesNotReport() {
        whenever(intent.getBooleanExtra(eq(EXTRA_LAUNCHED_FROM_NOTIFICATION), any())).thenReturn(true)

        reporter.onActivityCreated(activity, savedInstanceState = mock())

        verify(modalShownReporter, never()).reportModalShown()
    }

    @Test
    fun whenNotLaunchedFromNotificationThenDoesNotReport() {
        whenever(intent.getBooleanExtra(eq(EXTRA_LAUNCHED_FROM_NOTIFICATION), any())).thenReturn(false)

        reporter.onActivityCreated(activity, savedInstanceState = null)

        verify(modalShownReporter, never()).reportModalShown()
    }

    @Test
    fun whenActivityIntentIsNullThenDoesNotReport() {
        whenever(activity.intent).thenReturn(null)

        reporter.onActivityCreated(activity, savedInstanceState = null)

        verify(modalShownReporter, never()).reportModalShown()
    }

    @Test
    fun whenIntentExtrasThrowBadParcelableExceptionThenDoesNotCrashAndDoesNotReport() {
        whenever(intent.getBooleanExtra(eq(EXTRA_LAUNCHED_FROM_NOTIFICATION), any()))
            .thenThrow(BadParcelableException("ClassNotFoundException when unmarshalling: zxh"))

        reporter.onActivityCreated(activity, savedInstanceState = null)

        verify(modalShownReporter, never()).reportModalShown()
    }

    @Test
    fun whenIntentExtrasThrowRuntimeExceptionThenDoesNotCrashAndDoesNotReport() {
        whenever(intent.getBooleanExtra(eq(EXTRA_LAUNCHED_FROM_NOTIFICATION), any()))
            .thenThrow(RuntimeException("Parcel deserialization failed"))

        reporter.onActivityCreated(activity, savedInstanceState = null)

        verify(modalShownReporter, never()).reportModalShown()
    }
}
