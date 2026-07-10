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

package com.duckduckgo.app.browser.newaddressbaroption

import android.content.Context
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.AppScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface NewAddressBarPickerBottomSheetDialogFactory {
    fun create(
        context: Context,
        isLightMode: Boolean,
        callback: NewAddressBarCallback?,
    ): BottomSheetDialog
}

@ContributesBinding(AppScope::class)
class RealNewAddressBarPickerBottomSheetDialogFactory @Inject constructor(
    private val edgeToEdgeProvider: EdgeToEdgeProvider,
) : NewAddressBarPickerBottomSheetDialogFactory {
    override fun create(
        context: Context,
        isLightMode: Boolean,
        callback: NewAddressBarCallback?,
    ): BottomSheetDialog =
        NewAddressBarPickerBottomSheetDialog(
            context = context,
            isLightMode = isLightMode,
            callback = callback,
            edgeToEdgeEnabled = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.BOTTOM_SHEETS),
        )
}
