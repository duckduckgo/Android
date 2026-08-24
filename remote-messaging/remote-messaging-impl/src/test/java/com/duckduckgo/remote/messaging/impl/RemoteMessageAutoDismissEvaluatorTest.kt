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

package com.duckduckgo.remote.messaging.impl

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.remote.messaging.api.DisplayConditions
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aSmallMessage
import com.duckduckgo.remote.messaging.impl.pixels.RemoteMessagingPixels
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity
import com.duckduckgo.remote.messaging.store.RemoteMessageEntity.Status
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class RemoteMessageAutoDismissEvaluatorTest {

    private val remoteMessagingPixels: RemoteMessagingPixels = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock()

    private val testee = RealRemoteMessageAutoDismissEvaluator(remoteMessagingPixels, currentTimeProvider)

    @Test
    fun whenNoDisplayConditionsThenMessageIsKept() {
        assertFalse(testee.shouldAutoDismiss(aSmallMessage(), anEntity()))

        verifyNoInteractions(remoteMessagingPixels)
    }

    @Test
    fun whenImpressionsBelowCapThenMessageIsKept() {
        val message = aMessageWith(maxImpressions = 3)

        assertFalse(testee.shouldAutoDismiss(message, anEntity(impressions = 2)))

        verifyNoInteractions(remoteMessagingPixels)
    }

    @Test
    fun whenImpressionCapReachedThenMessageIsReported() {
        val message = aMessageWith(maxImpressions = 3)

        assertTrue(testee.shouldAutoDismiss(message, anEntity(impressions = 3)))

        verify(remoteMessagingPixels).fireRemoteMessageAutoDismissedPixel(message)
    }

    @Test
    fun whenImpressionsPastCapThenMessageIsReported() {
        val message = aMessageWith(maxImpressions = 3)

        assertTrue(testee.shouldAutoDismiss(message, anEntity(impressions = 4)))

        verify(remoteMessagingPixels).fireRemoteMessageAutoDismissedPixel(message)
    }

    @Test
    fun whenImpressionCapIsZeroOrNegativeThenMessageIsNeverCapped() {
        assertFalse(testee.shouldAutoDismiss(aMessageWith(maxImpressions = 0), anEntity(impressions = 10)))
        assertFalse(testee.shouldAutoDismiss(aMessageWith(maxImpressions = -1), anEntity(impressions = 10)))

        verifyNoInteractions(remoteMessagingPixels)
    }

    @Test
    fun whenExpiryThresholdReachedThenMessageIsReported() {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(TimeUnit.DAYS.toMillis(5))
        val message = aMessageWith(dismissAfterDaysShown = 5)

        assertTrue(testee.shouldAutoDismiss(message, anEntity(firstShownDate = 0L)))

        verify(remoteMessagingPixels).fireRemoteMessageAutoDismissedPixel(message)
    }

    @Test
    fun whenWithinExpiryThresholdThenMessageIsKept() {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(TimeUnit.DAYS.toMillis(4))

        assertFalse(testee.shouldAutoDismiss(aMessageWith(dismissAfterDaysShown = 5), anEntity(firstShownDate = 0L)))

        verifyNoInteractions(remoteMessagingPixels)
    }

    @Test
    fun whenNeverShownThenExpiryCannotRetireTheMessage() {
        assertFalse(testee.shouldAutoDismiss(aMessageWith(dismissAfterDaysShown = 5), anEntity(firstShownDate = null)))

        verifyNoInteractions(remoteMessagingPixels)
    }

    @Test
    fun whenExpiryThresholdIsZeroThenMessageNeverExpires() {
        assertFalse(testee.shouldAutoDismiss(aMessageWith(dismissAfterDaysShown = 0), anEntity(firstShownDate = 0L)))

        verifyNoInteractions(remoteMessagingPixels)
    }

    @Test
    fun whenBothExpiredAndCappedThenMessageIsReportedOnce() {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(TimeUnit.DAYS.toMillis(5))
        val message = aMessageWith(dismissAfterDaysShown = 5, maxImpressions = 1)

        assertTrue(testee.shouldAutoDismiss(message, anEntity(firstShownDate = 0L, impressions = 1)))

        verify(remoteMessagingPixels).fireRemoteMessageAutoDismissedPixel(message)
    }

    private fun aMessageWith(
        dismissAfterDaysShown: Int? = null,
        maxImpressions: Int? = null,
    ): RemoteMessage = aSmallMessage().copy(
        displayConditions = DisplayConditions(
            trigger = null,
            dismissAfterDaysShown = dismissAfterDaysShown,
            maxImpressions = maxImpressions,
        ),
    )

    private fun anEntity(
        firstShownDate: Long? = null,
        impressions: Int = 0,
    ) = RemoteMessageEntity(
        id = "id",
        message = "message",
        status = Status.SCHEDULED,
        firstShownDate = firstShownDate,
        impressions = impressions,
    )
}
