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

package com.duckduckgo.autofill.impl.importing.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillImportLaunchSource.MainAppSettings
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.importing.InSettingsPasswordImportPromoRules
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_TAPPED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_IMPORT_GOOGLE_PASSWORDS_MAIN_APP_SETTINGS_HIDDEN
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialog
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialog.ImportPasswordsDialog
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialog.ImportPasswordsDialog.Companion.KEY_IMPORT_SUCCESS
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.listitem.OneLineListItem
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.settings.api.CompleteSetupSettingsPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

@ContributesMultibinding(ActivityScope::class)
@PriorityKey(100)
class ImportGooglePasswordMainAppSettings @Inject constructor(
    private val pixel: Pixel,
    private val autofillStore: InternalAutofillStore,
    private val dispatchers: DispatcherProvider,
    private val promoRules: InSettingsPasswordImportPromoRules,
    private val activity: AppCompatActivity,
) : CompleteSetupSettingsPlugin {

    override fun getView(context: Context): View {
        return OneLineListItem(activity).apply {
            setLeadingIconResource(R.drawable.key_color_import_24)

            setPrimaryText(activity.getString(R.string.passwords_import_promo_in_settings_title))
            setOnClickListener {
                onLaunchImportFlow(activity, this)
            }

            configureOverflowMenu()

            gone()
        }.also {
            it.showIfPermitted()
        }
    }

    private fun OneLineListItem.showIfPermitted() {
        activity.lifecycleScope.launch {
            if (promoRules.canShowPromo()) {
                show()
            }
        }
    }

    private fun onLaunchImportFlow(activity: AppCompatActivity, rootView: View) {
        listenForImportResult(activity, rootView)

        val dialog = ImportFromGooglePasswordsDialog.instance(importSource = MainAppSettings, tabId = DIALOG_TAG)
        dialog.show(activity.supportFragmentManager, DIALOG_TAG)

        val params = mapOf("source" to MainAppSettings.value)
        pixel.fire(AUTOFILL_IMPORT_GOOGLE_PASSWORDS_EMPTY_STATE_CTA_BUTTON_TAPPED, params)
    }

    private fun listenForImportResult(activity: AppCompatActivity, rootView: View) {
        val resultKey = ImportPasswordsDialog.resultKey(tabId = DIALOG_TAG)

        activity.supportFragmentManager.setFragmentResultListener(resultKey, activity) { _: String, result: Bundle ->
            logcat { "Autofill-import: Got import passwords result: $result" }
            if (result.getBoolean(KEY_IMPORT_SUCCESS)) {
                rootView.gone()
            }
        }
    }

    private fun OneLineListItem.configureOverflowMenu() {
        showTrailingIcon()
        setTrailingIconClickListener { overflowView ->
            val layoutInflater = LayoutInflater.from(context)
            val popupMenu = buildPopupMenu(this, layoutInflater)
            popupMenu.show(this, overflowView)
        }
    }

    private fun buildPopupMenu(
        rootView: View,
        layoutInflater: LayoutInflater,
    ): PopupMenu {
        val popupMenu = PopupMenu(layoutInflater, R.layout.popup_window_import_passwords_menu)
        val hideButton = popupMenu.contentView.findViewById<View>(R.id.hide)

        popupMenu.apply {
            onMenuItemClicked(hideButton) {
                rootView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(dispatchers.main()) {
                    rootView.gone()
                    withContext(dispatchers.io()) {
                        autofillStore.hasDismissedMainAppSettingsPromo = true
                        pixel.fire(AUTOFILL_IMPORT_GOOGLE_PASSWORDS_MAIN_APP_SETTINGS_HIDDEN)
                    }
                }
            }
        }

        return popupMenu
    }

    private companion object {
        private const val DIALOG_TAG = "ImportGooglePasswordsDialog"
    }
}
