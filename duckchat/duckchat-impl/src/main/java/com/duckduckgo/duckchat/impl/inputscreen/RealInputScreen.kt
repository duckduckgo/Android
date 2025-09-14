/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.inputscreen

import android.content.Context
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.inputscreen.InputScreen
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption.ChoiceSelectionCallback
import com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption.NewAddressBarOptionBottomSheetDialogFactory
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealInputScreen @Inject constructor(
    private val newAddressBarOptionBottomSheetDialogFactory: NewAddressBarOptionBottomSheetDialogFactory,
    private val duckChatInternal: DuckChatInternal,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : InputScreen {

    override fun showNewAddressBarOptionChoiceScreen(context: Context, isDarkThemeEnabled: Boolean) {
        newAddressBarOptionBottomSheetDialogFactory.create(
            context = context,
            isDarkThemeEnabled = isDarkThemeEnabled,
            choiceSelectionCallback = object : ChoiceSelectionCallback {
                override fun onSearchAndDuckAiSelected() {
                    appCoroutineScope.launch(dispatchers.io()) {
                        duckChatInternal.setInputScreenUserSetting(true)
                    }
                }
            },
        ).show()
    }
}
