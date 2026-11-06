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

package com.duckduckgo.pir.impl.dashboard.entrypoint

import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreeScanEntryPoint.APP_SETTINGS_POST_SCAN
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreeScanEntryPoint.APP_SETTINGS_PRE_SCAN
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreeScanEntryPoint.FREE_SCAN_BANNER
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreeScanEntryPoint.SCAN_COMPLETE_NOTIFICATION
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreeScanEntryPoint.VIEW_RESULTS_BANNER
import org.junit.Assert.assertEquals
import org.junit.Test

class PirFreeScanEntryPointTest {

    @Test
    fun whenEntryPointIsKnownThenOriginCarriesItsToken() {
        assertEquals("funnel_freescan_android__freescanbanner", freeScanOrigin(BASE_ORIGIN, FREE_SCAN_BANNER))
        assertEquals("funnel_freescan_android__viewresultsbanner", freeScanOrigin(BASE_ORIGIN, VIEW_RESULTS_BANNER))
        assertEquals("funnel_freescan_android__appsettingsprescan", freeScanOrigin(BASE_ORIGIN, APP_SETTINGS_PRE_SCAN))
        assertEquals("funnel_freescan_android__appsettingspostscan", freeScanOrigin(BASE_ORIGIN, APP_SETTINGS_POST_SCAN))
        assertEquals("funnel_freescan_android__scancompletenotification", freeScanOrigin(BASE_ORIGIN, SCAN_COMPLETE_NOTIFICATION))
    }

    @Test
    fun whenEntryPointIsUnknownThenOriginIsTheBaseOrigin() {
        assertEquals(BASE_ORIGIN, freeScanOrigin(BASE_ORIGIN, null))
    }

    @Test
    fun whenTokensAreListedThenTheyMatchTheValuesTheWebUiAccepts() {
        assertEquals(
            listOf("freescanbanner", "viewresultsbanner", "appsettingsprescan", "appsettingspostscan", "scancompletenotification"),
            PirFreeScanEntryPoint.entries.map { it.token },
        )
    }

    private companion object {
        const val BASE_ORIGIN = "funnel_freescan_android"
    }
}
