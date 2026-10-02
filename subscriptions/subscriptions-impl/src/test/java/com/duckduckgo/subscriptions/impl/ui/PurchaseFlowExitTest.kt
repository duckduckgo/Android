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

package com.duckduckgo.subscriptions.impl.ui

import com.duckduckgo.subscriptions.api.SubscriptionPurchaseCompletion.GO_TO_SETTINGS
import com.duckduckgo.subscriptions.api.SubscriptionPurchaseCompletion.RETURN_TO_CALLER
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseFlowExitTest {

    @Test
    fun whenCompletionIsGoToSettingsThenExitsToSettings() {
        assertEquals(
            PurchaseFlowExit.GoToSettings,
            purchaseFlowExit(completion = GO_TO_SETTINGS, isActivateUrl = false),
        )
    }

    @Test
    fun whenCompletionIsReturnToCallerThenExitsToCaller() {
        assertEquals(
            PurchaseFlowExit.ReturnToCaller,
            purchaseFlowExit(completion = RETURN_TO_CALLER, isActivateUrl = false),
        )
    }

    @Test
    fun whenUrlIsActivateThenResultIsReturnedEvenForReturnToCaller() {
        assertEquals(
            PurchaseFlowExit.ActivateResult,
            purchaseFlowExit(completion = RETURN_TO_CALLER, isActivateUrl = true),
        )
    }

    @Test
    fun whenUrlIsActivateThenResultIsReturnedForGoToSettings() {
        assertEquals(
            PurchaseFlowExit.ActivateResult,
            purchaseFlowExit(completion = GO_TO_SETTINGS, isActivateUrl = true),
        )
    }
}
