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

package com.duckduckgo.fingerprintprotection.impl.features.fingerprintingbattery

import com.duckduckgo.fingerprintprotection.store.FingerprintingBatteryEntity
import com.duckduckgo.fingerprintprotection.store.features.fingerprintingbattery.FingerprintingBatteryRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FingerprintingBatteryContentScopeConfigPluginTest {

    lateinit var testee: FingerprintingBatteryContentScopeConfigPlugin

    private val mockFingerprintingBatteryRepository: FingerprintingBatteryRepository = mock()

    @Before
    fun before() {
        testee = FingerprintingBatteryContentScopeConfigPlugin(mockFingerprintingBatteryRepository)
    }

    @Test
    fun whenGetConfigThenReturnCorrectlyFormattedJson() {
        whenever(mockFingerprintingBatteryRepository.fingerprintingBatteryEntity).thenReturn(FingerprintingBatteryEntity(json = config))
        assertEquals("\"fingerprintingBattery\":$config", testee.config())
    }

    @Test
    fun whenGetPreferencesThenReturnNull() {
        assertNull(testee.preferences())
    }

    companion object {
        const val config = "{\"key\":\"value\"}"
    }
}
