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

package com.duckduckgo.app.process

import com.duckduckgo.app.process.ProcessDetector.DuckDuckGoProcess
import com.duckduckgo.app.process.ProcessDetector.DuckDuckGoProcess.*
import org.junit.Assert.*
import org.junit.Test

class ProcessDetectorTest {

    private val testee: ProcessDetector = ProcessDetector()

    @Test
    fun whenProcessNameHasNoColonThenProcessDetectedAsBrowser() {
        val processName = "com.duckduckgo.mobile.android.vpn"
        assertBrowserProcess(testee.detectProcess(processName))
    }

    @Test
    fun whenProcessNameEndsWithColonVpnThenProcessDetectedAsVpn() {
        val processName = "com.duckduckgo.mobile.android.vpn:vpn"
        assertVpnProcess(testee.detectProcess(processName))
    }

    @Test
    fun whenProcessNameEndsWithUnknownStringAfterColonThenProcessDetectedAsUnknown() {
        val processName = "com.duckduckgo.mobile.android.vpn:foo"
        assertUnknownProcess(testee.detectProcess(processName))
    }

    @Test
    fun whenEmptyProcessNameThenProcessDetectedAsBrowser() {
        val processName = ""
        assertBrowserProcess(testee.detectProcess(processName))
    }

    private fun assertBrowserProcess(process: DuckDuckGoProcess) {
        assertTrue(String.format("%s isn't the expected process type", process), process is BrowserProcess)
    }

    private fun assertVpnProcess(process: DuckDuckGoProcess) {
        assertTrue(String.format("%s isn't the expected process type", process), process is VpnProcess)
    }

    private fun assertUnknownProcess(process: DuckDuckGoProcess) {
        assertTrue(String.format("%s isn't the expected process type", process), process is UnknownProcess)
    }
}
