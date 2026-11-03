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

package com.duckduckgo.duckchat.impl.nativeinput.footer

/** Records footer actions so tests can assert what a plugin asked the input to do. */
class FakeNativeInputFooterHost(
    var draft: NativeInputFooterDraft = NativeInputFooterDraft(hasImages = false, fileMimeTypes = emptyList(), selectedTool = null),
) : NativeInputFooterHost {
    val selectedModelIds = mutableListOf<String>()
    var startUsingWeeklyLimitCalls = 0
    val purchaseOrigins = mutableListOf<String>()

    override fun draft(): NativeInputFooterDraft = draft

    override fun selectModel(modelId: String) {
        selectedModelIds += modelId
    }

    override fun startUsingWeeklyLimit() {
        startUsingWeeklyLimitCalls++
    }

    override fun openSubscriptionPurchase(origin: String) {
        purchaseOrigins += origin
    }
}
