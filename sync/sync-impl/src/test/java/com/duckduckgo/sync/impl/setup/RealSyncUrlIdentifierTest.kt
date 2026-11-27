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

package com.duckduckgo.sync.impl.setup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealSyncUrlIdentifierTest {

    private val syncPairingUrlFeature = FakeSyncPairingUrlFeature()

    private val testee = RealSyncUrlIdentifier(syncPairingUrlFeature)

    @Test
    fun whenInputIsNullThenShouldNotDelegate() {
        assertFalse(testee.shouldDelegateToSyncSetup(intentText = null))
    }

    @Test
    fun whenInputIsEmptyStringThenShouldNotDelegate() {
        assertFalse(testee.shouldDelegateToSyncSetup(intentText = ""))
    }

    @Test
    fun whenInputStartsWithUrlBaseButIsIncompleteThenShouldNotDelegate() {
        assertFalse(testee.shouldDelegateToSyncSetup(intentText = SyncBarcodeUrl.URL_BASE))
    }

    @Test
    fun whenInputIsValidSyncSetupUrlThenShouldDelegate() {
        val inputText = SyncBarcodeUrl(
            webSafeB64EncodedCode = "code",
            deviceName = "deviceName",
        ).asUrl()
        assertTrue(testee.shouldDelegateToSyncSetup(intentText = inputText))
    }

    @Test
    fun whenPairingUrlTypedThenShouldOpenSyncPairing() {
        syncPairingUrlFeature.isEnabled = true

        assertTrue(testee.shouldOpenSyncPairing("https://duck.com/pair"))
        assertTrue(testee.shouldOpenSyncPairing("https://duck.ai/pair#code2=ABC"))
    }

    @Test
    fun whenOtherUrlTypedThenShouldNotOpenSyncPairing() {
        syncPairingUrlFeature.isEnabled = true

        assertFalse(testee.shouldOpenSyncPairing("https://duck.ai/chat"))
    }

    @Test
    fun whenFeatureDisabledThenShouldNotOpenSyncPairing() {
        syncPairingUrlFeature.isEnabled = false

        assertFalse(testee.shouldOpenSyncPairing("https://duck.com/pair"))
    }

    private class FakeSyncPairingUrlFeature : SyncPairingUrlFeature {
        override var isEnabled = false
    }
}
