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

package com.duckduckgo.fingerprintprotection.impl.features.fingerprintingtemporarystorage

import com.duckduckgo.fingerprintprotection.api.FingerprintProtectionFeatureName
import com.duckduckgo.fingerprintprotection.store.FingerprintingTemporaryStorageEntity
import com.duckduckgo.fingerprintprotection.store.features.fingerprintingtemporarystorage.FingerprintingTemporaryStorageRepository
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor

class FingerprintingTemporaryStorageFeaturePluginTest {
    lateinit var testee: FingerprintingTemporaryStorageFeaturePlugin

    private val mockFingerprintingTemporaryStorageRepository: FingerprintingTemporaryStorageRepository = mock()

    @Before
    fun before() {
        testee = FingerprintingTemporaryStorageFeaturePlugin(mockFingerprintingTemporaryStorageRepository)
    }

    @Test
    fun whenFeatureNameDoesNotMatchFingerprintingTemporaryStorageThenReturnFalse() {
        FingerprintProtectionFeatureName.values().filter { it != FEATURE_NAME }.forEach {
            assertFalse(testee.store(it.value, JSON_STRING))
        }
    }

    @Test
    fun whenFeatureNameMatchesFingerprintingTemporaryStorageThenReturnTrue() {
        assertTrue(testee.store(FEATURE_NAME_VALUE, JSON_STRING))
    }

    @Test
    fun whenFeatureNameMatchesFingerprintingTemporaryStorageThenUpdateAll() {
        testee.store(FEATURE_NAME_VALUE, JSON_STRING)
        val captor = argumentCaptor<FingerprintingTemporaryStorageEntity>()
        verify(mockFingerprintingTemporaryStorageRepository).updateAll(captor.capture())
        assertEquals(JSON_STRING, captor.firstValue.json)
    }

    companion object {
        private val FEATURE_NAME = FingerprintProtectionFeatureName.FingerprintingTemporaryStorage
        private val FEATURE_NAME_VALUE = FEATURE_NAME.value
        private const val JSON_STRING = "{\"key\":\"value\"}"
    }
}
