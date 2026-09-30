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

package com.duckduckgo.pir.impl.store

import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.pir.impl.store.PirFreemiumFirstScanResult.MATCHES_FOUND
import com.duckduckgo.pir.impl.store.PirFreemiumFirstScanResult.NO_MATCHES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealPirFreemiumDataStoreTest {

    private val preferences = InMemorySharedPreferences()
    private val mockSharedPreferencesProvider: SharedPreferencesProvider = mock()

    private lateinit var testee: RealPirFreemiumDataStore

    @Before
    fun setUp() {
        // Only the multiprocess file is stubbed: scans write from :pir while Settings reads from :main.
        whenever(mockSharedPreferencesProvider.getSharedPreferences(any(), eq(true), any())).thenReturn(preferences)

        testee = RealPirFreemiumDataStore(mockSharedPreferencesProvider)
    }

    @Test
    fun whenNoScanHasCompletedThenFirstScanResultIsNull() {
        assertNull(testee.firstScanResult)
    }

    @Test
    fun whenMatchesFoundIsRecordedThenItIsReadBack() {
        testee.recordFirstScanResult(MATCHES_FOUND)

        assertEquals(MATCHES_FOUND, testee.firstScanResult)
    }

    @Test
    fun whenNoMatchesIsRecordedThenItIsReadBack() {
        testee.recordFirstScanResult(NO_MATCHES)

        assertEquals(NO_MATCHES, testee.firstScanResult)
    }

    @Test
    fun whenNoMatchesIsRecordedThenALaterMatchesFoundReplacesIt() {
        testee.recordFirstScanResult(NO_MATCHES)
        testee.recordFirstScanResult(MATCHES_FOUND)

        assertEquals(MATCHES_FOUND, testee.firstScanResult)
    }

    @Test
    fun whenMatchesFoundIsRecordedThenALaterNoMatchesIsIgnored() {
        testee.recordFirstScanResult(MATCHES_FOUND)
        testee.recordFirstScanResult(NO_MATCHES)

        assertEquals(MATCHES_FOUND, testee.firstScanResult)
    }

    @Test
    fun whenResetThenFirstScanResultIsCleared() {
        testee.recordFirstScanResult(MATCHES_FOUND)

        testee.reset()

        assertNull(testee.firstScanResult)
    }

    @Test
    fun whenResetThenANewFirstScanResultCanBeRecorded() {
        testee.recordFirstScanResult(NO_MATCHES)
        testee.reset()

        testee.recordFirstScanResult(MATCHES_FOUND)

        assertEquals(MATCHES_FOUND, testee.firstScanResult)
    }

    @Test
    fun whenResetThenActivationIsClearedAlongsideTheResult() {
        testee.activate(timestampMillis = 1_000L)
        testee.recordFirstScanResult(MATCHES_FOUND)

        testee.reset()

        assertFalse(testee.didActivate)
        assertEquals(0L, testee.firstProfileSavedTimestamp)
    }

    @Test
    fun whenStoredValueIsUnrecognisedThenFirstScanResultIsNull() {
        preferences.edit().putString(KEY_FIRST_SCAN_RESULT, "SOME_FUTURE_RESULT").commit()

        assertNull(testee.firstScanResult)
    }

    @Test
    fun whenStoredValueIsUnrecognisedThenANewResultReplacesIt() {
        preferences.edit().putString(KEY_FIRST_SCAN_RESULT, "SOME_FUTURE_RESULT").commit()

        testee.recordFirstScanResult(NO_MATCHES)

        assertEquals(NO_MATCHES, testee.firstScanResult)
    }

    private companion object {
        // Hardcoded, not shared with production, so renaming the persisted key fails a test instead of silently dropping stored results.
        const val KEY_FIRST_SCAN_RESULT = "KEY_FIRST_SCAN_RESULT"
    }
}
