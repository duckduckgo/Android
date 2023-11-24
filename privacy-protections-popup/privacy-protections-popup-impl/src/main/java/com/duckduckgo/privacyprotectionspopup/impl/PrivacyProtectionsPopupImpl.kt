/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.privacyprotectionspopup.impl

import android.content.Context
import android.view.View
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopup
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupViewState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PrivacyProtectionsPopupImpl(
    private val anchor: View,
) : PrivacyProtectionsPopup {

    private val context: Context get() = anchor.context
    private val _events = MutableSharedFlow<PrivacyProtectionsPopupUiEvent>(extraBufferCapacity = 1)
    private var visible = false

    override fun setViewState(viewState: PrivacyProtectionsPopupViewState) {
        if (viewState.visible != visible) {
            visible = viewState.visible

            if (visible) {
                showPopup()
            } else {
                dismissPopup()
            }
        }
    }

    override val events: Flow<PrivacyProtectionsPopupUiEvent> = _events.asSharedFlow()

    private fun showPopup() {
        // TODO
    }

    private fun dismissPopup() {
        // TODO
    }
}
