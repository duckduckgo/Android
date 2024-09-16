/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.sync.impl.error

import com.duckduckgo.sync.api.engine.SyncableType
import com.duckduckgo.sync.impl.API_CODE
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.store.model.SyncApiErrorType.OBJECT_LIMIT_EXCEEDED
import com.duckduckgo.sync.store.model.SyncApiErrorType.REQUEST_SIZE_LIMIT_EXCEEDED
import com.duckduckgo.sync.store.model.SyncApiErrorType.TOO_MANY_REQUESTS
import com.duckduckgo.sync.store.model.SyncApiErrorType.VALIDATION_ERROR
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class SyncApiErrorRecorderTest {

    private val syncPixels: SyncPixels = mock()
    private val syncApiErrorRepository: SyncApiErrorRepository = mock()

    private val apiErrorReporter = RealSyncApiErrorRecorder(syncPixels, syncApiErrorRepository)

    @Test
    fun wheneverCountLimitErrorReportedThenRepositoryAddsError() {
        val feature = SyncableType.BOOKMARKS
        val error = Error(API_CODE.COUNT_LIMIT.code, "")

        apiErrorReporter.record(feature, error)

        verify(syncApiErrorRepository).addError(feature, OBJECT_LIMIT_EXCEEDED)
        verify(syncPixels).fireDailySyncApiErrorPixel(feature, error)
    }

    @Test
    fun wheneverContentTooLargeErrorReportedThenRepositoryAddsError() {
        val feature = SyncableType.BOOKMARKS
        val error = Error(API_CODE.CONTENT_TOO_LARGE.code, "")

        apiErrorReporter.record(feature, error)

        verify(syncApiErrorRepository).addError(feature, REQUEST_SIZE_LIMIT_EXCEEDED)
        verify(syncPixels).fireDailySyncApiErrorPixel(feature, error)
    }

    @Test
    fun wheneverValidationErrorReportedThenRepositoryAddsError() {
        val feature = SyncableType.BOOKMARKS
        val error = Error(API_CODE.VALIDATION_ERROR.code, "")

        apiErrorReporter.record(feature, error)

        verify(syncApiErrorRepository).addError(feature, VALIDATION_ERROR)
        verify(syncPixels).fireDailySyncApiErrorPixel(feature, error)
    }

    @Test
    fun wheneverFirstTooManyRequestsErrorReportedThenRepositoryAddsError() {
        val feature = SyncableType.BOOKMARKS
        val error = Error(API_CODE.TOO_MANY_REQUESTS_1.code, "")

        apiErrorReporter.record(feature, error)

        verify(syncApiErrorRepository).addError(feature, TOO_MANY_REQUESTS)
        verify(syncPixels).fireDailySyncApiErrorPixel(feature, error)
    }

    @Test
    fun wheneverSecondTooManyRequestsErrorReportedThenRepositoryAddsError() {
        val feature = SyncableType.BOOKMARKS
        val error = Error(API_CODE.TOO_MANY_REQUESTS_2.code, "")

        apiErrorReporter.record(feature, error)

        verify(syncApiErrorRepository).addError(feature, TOO_MANY_REQUESTS)
        verify(syncPixels).fireDailySyncApiErrorPixel(feature, error)
    }
}
