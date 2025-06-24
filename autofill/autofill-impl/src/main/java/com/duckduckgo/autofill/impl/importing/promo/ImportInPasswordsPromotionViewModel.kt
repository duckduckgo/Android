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

package com.duckduckgo.autofill.impl.importing.promo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillImportLaunchSource.PasswordManagementPromo
import com.duckduckgo.autofill.impl.importing.promo.ImportInPasswordsPromotionViewModel.Command.DismissImport
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_TAPPED
import com.duckduckgo.autofill.impl.store.AutofillEffect
import com.duckduckgo.autofill.impl.store.AutofillEffectDispatcher
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ViewScope::class)
class ImportInPasswordsPromotionViewModel @Inject constructor(
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val promoEventDispatcher: AutofillEffectDispatcher,
    private val importInPasswordsVisibility: ImportInPasswordsVisibility,
) : ViewModel() {

    sealed interface Command {
        data object DismissImport : Command
    }

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    // want to ensure this pixel doesn't trigger repeatedly as it's scrolled in and out of the list
    private var promoDisplayedPixelSent = false

    fun onPromoShown() {
        if (!promoDisplayedPixelSent) {
            promoDisplayedPixelSent = true
            val params = mapOf("source" to PasswordManagementPromo.value)
            pixel.fire(AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_SHOWN, params)
        }
    }

    fun onUserClickedToImport() {
        viewModelScope.launch(dispatchers.io()) {
            promoEventDispatcher.emit(
                AutofillEffect.LaunchImportPasswords(
                    source = PasswordManagementPromo,
                ),
            )
            val params = mapOf("source" to PasswordManagementPromo.value)
            pixel.fire(AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_TAPPED, params)
        }
    }

    fun onUserDismissedPromo() {
        viewModelScope.launch(dispatchers.io()) {
            importInPasswordsVisibility.onPromoDismissed()
            command.send(DismissImport)
            pixel.fire(
                pixel = AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_DISMISSED,
                parameters = mapOf("source" to PasswordManagementPromo.value),
                encodedParameters = emptyMap(),
            )
        }
    }
}
