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

import com.duckduckgo.subscriptions.api.SubscriptionPurchaseCompletion
import com.duckduckgo.subscriptions.api.SubscriptionPurchaseCompletion.RETURN_TO_CALLER

/**
 * Where the purchase screen leaves the user when it finishes. Extracted from the Activity so the
 * decision can be tested; the Activity performs it.
 */
internal sealed interface PurchaseFlowExit {

    /** Hand a result back to the caller that launched this screen for a result. */
    data object ActivateResult : PurchaseFlowExit

    /** Finish only, revealing whichever screen started the purchase. */
    data object ReturnToCaller : PurchaseFlowExit

    /** Navigate to Settings, clearing anything above it. */
    data object GoToSettings : PurchaseFlowExit
}

internal fun purchaseFlowExit(
    completion: SubscriptionPurchaseCompletion,
    isActivateUrl: Boolean,
): PurchaseFlowExit = when {
    // The activate flow is started for a result, so its caller must still get one — otherwise the
    // restore screen never learns the subscription was activated.
    isActivateUrl -> PurchaseFlowExit.ActivateResult
    completion == RETURN_TO_CALLER -> PurchaseFlowExit.ReturnToCaller
    else -> PurchaseFlowExit.GoToSettings
}
