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

package com.duckduckgo.autofill.impl.ui.credential.management.importpassword

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_COPIED_DESKTOP_LINK
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_PASSWORDS_SHARED_DESKTOP_LINK
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionInteractionHandler
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionInteractionHandler.Interaction
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class ImportPasswordsDesktopAppPromotionInteractionHandler @Inject constructor(
    private val pixel: Pixel,
) : DesktopAppPromotionInteractionHandler {

    override val handlerId: String = HANDLER_ID

    override suspend fun onInteraction(interaction: Interaction): Boolean {
        return when (interaction) {
            Interaction.SHARE_CLICKED -> {
                pixel.fire(AUTOFILL_IMPORT_PASSWORDS_SHARED_DESKTOP_LINK)
                true
            }
            Interaction.LINK_COPIED -> {
                pixel.fire(AUTOFILL_IMPORT_PASSWORDS_COPIED_DESKTOP_LINK)
                true
            }
            Interaction.IMPRESSION, Interaction.SHARE_COMPLETED, Interaction.DISMISSED -> true
        }
    }

    companion object {
        const val HANDLER_ID = "autofill_import_passwords_desktop_app"
    }
}
