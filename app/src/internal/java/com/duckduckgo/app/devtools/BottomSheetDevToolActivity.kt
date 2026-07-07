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

package com.duckduckgo.app.devtools

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import com.duckduckgo.adblocking.impl.duckplayer.ui.DuckPlayerPrimeBottomSheet
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.defaultbrowsing.prompts.ui.DefaultBrowserBottomSheetDialog
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.onboardingquicksetup.ui.QuickSetupAddressBarPositionBottomSheet
import com.duckduckgo.app.onboardingquicksetup.ui.QuickSetupSearchOptionsBottomSheet
import com.duckduckgo.app.onboardingquicksetup.ui.RemoveWidgetInstructionsBottomSheet
import com.duckduckgo.autofill.api.AutofillImportLaunchSource
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType
import com.duckduckgo.autofill.impl.email.EmailProtectionChooseEmailFragment
import com.duckduckgo.autofill.impl.email.incontext.prompt.EmailProtectionInContextSignUpPromptFragment
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.google.ImportFromGooglePasswordsDialog
import com.duckduckgo.autofill.impl.ui.credential.passwordgeneration.AutofillUseGeneratedPasswordDialogFragment
import com.duckduckgo.autofill.impl.ui.credential.saving.AutofillSavingCredentialsDialogFragment
import com.duckduckgo.autofill.impl.ui.credential.selecting.AutofillSelectCredentialsDialogFragment
import com.duckduckgo.autofill.impl.ui.credential.updating.AutofillUpdatingExistingCredentialsDialogFragment
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.downloads.api.DownloadConfirmation
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.mobile.android.vpn.ui.alwayson.AlwaysOnAlertDialogFragment
import com.duckduckgo.networkprotection.impl.autoexclude.VpnAutoExcludePromptFragment
import com.duckduckgo.networkprotection.impl.management.alwayson.NetworkProtectionAlwaysOnDialogFragment
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpGeoswitchingCityChoiceDialogFragment
import com.duckduckgo.networkprotection.store.db.VpnIncompatibleApp
import com.duckduckgo.savedsites.impl.importing.ImportFromGoogleBookmarksPreImportDialog
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class BottomSheetDevToolActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var downloadConfirmation: DownloadConfirmation

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        // Title
        val titleView = AppCompatTextView(this).apply {
            text = "Bottom Sheet DevTools"
            textSize = 20f
            setPadding(16, 16, 16, 16)
        }
        linearLayout.addView(titleView)

        // Button 1: EmailProtectionChooseEmailFragment
        linearLayout.addView(createButton("1. EmailProtectionChooseEmail") {
            try {
                EmailProtectionChooseEmailFragment.instance(
                    personalDuckAddress = "debug@duck.com",
                    url = "https://example.com",
                    tabId = "debug-tab-id",
                ).show(supportFragmentManager, "devtool_1")
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Button 2: EmailProtectionInContextSignUpPromptFragment
        linearLayout.addView(createButton("2. EmailProtectionInContextSignUpPrompt") {
            try {
                EmailProtectionInContextSignUpPromptFragment.instance(
                    tabId = "debug-tab-id",
                ).show(supportFragmentManager, "devtool_2")
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Button 3: ImportFromGooglePasswordsDialog
        linearLayout.addView(createButton("3. ImportFromGooglePasswordsDialog") {
            try {
                ImportFromGooglePasswordsDialog.instance(
                    importSource = AutofillImportLaunchSource.AutofillSettings,
                    tabId = "debug-tab-id",
                    originalUrl = "https://example.com",
                ).show(supportFragmentManager, "devtool_3")
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Button 4: AutofillUseGeneratedPasswordDialogFragment
        linearLayout.addView(createButton("4. AutofillUseGeneratedPassword") {
            try {
                AutofillUseGeneratedPasswordDialogFragment.instance(
                    url = "https://example.com",
                    username = "debuguser",
                    generatedPassword = "DebugPassword123!",
                    tabId = "debug-tab-id",
                ).show(supportFragmentManager, "devtool_4")
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Button 5: AutofillSavingCredentialsDialogFragment
        linearLayout.addView(createButton("5. AutofillSavingCredentials") {
            try {
                val dummyCredentials = LoginCredentials(
                    domain = "example.com",
                    username = "debug@example.com",
                    password = "DebugPassword123!",
                )
                AutofillSavingCredentialsDialogFragment.instance(
                    url = "https://example.com",
                    credentials = dummyCredentials,
                    tabId = "debug-tab-id",
                ).show(supportFragmentManager, "devtool_5")
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Button 6: AutofillSelectCredentialsDialogFragment
        linearLayout.addView(createButton("6. AutofillSelectCredentials") {
            try {
                val dummyCredentials = LoginCredentials(
                    domain = "example.com",
                    username = "debug@example.com",
                    password = "DebugPassword123!",
                )
                AutofillSelectCredentialsDialogFragment.instance(
                    url = "https://example.com",
                    credentials = listOf(dummyCredentials),
                    triggerType = LoginTriggerType.USER_INITIATED,
                    tabId = "debug-tab-id",
                ).show(supportFragmentManager, "devtool_6")
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Button 7: AutofillUpdatingExistingCredentialsDialogFragment
        linearLayout.addView(createButton("7. AutofillUpdatingExistingCredentials") {
            try {
                val dummyCredentials = LoginCredentials(
                    domain = "example.com",
                    username = "debug@example.com",
                    password = "DebugPassword123!",
                )
                AutofillUpdatingExistingCredentialsDialogFragment.instance(
                    url = "https://example.com",
                    credentials = dummyCredentials,
                    tabId = "debug-tab-id",
                    credentialUpdateType = CredentialUpdateType.Username,
                ).show(supportFragmentManager, "devtool_7")
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Button 8: DownloadConfirmationFragment
        linearLayout.addView(createButton("8. DownloadConfirmation") {
            try {
                val dummyFileDownload = PendingFileDownload(
                    url = "https://example.com/file.pdf",
                    subfolder = "Downloads",
                    browserMode = BrowserMode.REGULAR,
                )
                val fragment = downloadConfirmation.instance(dummyFileDownload)
                fragment.show(supportFragmentManager, "devtool_8")
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Button 9: AlwaysOnAlertDialogFragment
        linearLayout.addView(createButton("9. VpnAlwaysOnAlertDialog") {
            try {
                AlwaysOnAlertDialogFragment.newAlwaysOnDialog(
                    object : AlwaysOnAlertDialogFragment.Listener {
                        override fun onGoToSettingsClicked() {}
                        override fun onCanceled() {}
                    },
                ).show(supportFragmentManager, "devtool_9")
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Button 10: VpnAutoExcludePromptFragment
        linearLayout.addView(createButton("10. VpnAutoExcludePrompt") {
            try {
                VpnAutoExcludePromptFragment.instance(
                    incompatibleApps = listOf(VpnIncompatibleApp(packageName = "com.debug.incompatible.app")),
                    source = VpnAutoExcludePromptFragment.Companion.Source.EXCLUSION_LIST_SCREEN,
                ).show(supportFragmentManager, "devtool_10")
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Button 11: NetworkProtectionAlwaysOnDialogFragment
        linearLayout.addView(createButton("11. NetworkProtectionAlwaysOnDialog") {
            try {
                NetworkProtectionAlwaysOnDialogFragment.newPromotionDialog(
                    object : NetworkProtectionAlwaysOnDialogFragment.Listener {
                        override fun onGoToSettingsClicked() {}
                        override fun onCanceled() {}
                    },
                ).show(supportFragmentManager, "devtool_11")
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Button 12: NetpGeoswitchingCityChoiceDialogFragment
        linearLayout.addView(createButton("12. NetpGeoswitchingCityChoice") {
            try {
                NetpGeoswitchingCityChoiceDialogFragment.instance(
                    countryName = "Debugland",
                    cities = arrayListOf("Debug City"),
                ).show(supportFragmentManager, "devtool_12")
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Button 13: ImportFromGoogleBookmarksPreImportDialog
        linearLayout.addView(createButton("13. ImportFromGoogleBookmarksPreImport") {
            try {
                ImportFromGoogleBookmarksPreImportDialog.instance()
                    .show(supportFragmentManager, "devtool_13")
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Button 14: DuckPlayerPrimeBottomSheet
        linearLayout.addView(createButton("14. DuckPlayerPrimeBottomSheet") {
            try {
                DuckPlayerPrimeBottomSheet.newInstance(fromDuckPlayerPage = false)
                    .show(supportFragmentManager, null)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Button 15: QuickSetupAddressBarPositionBottomSheet
        linearLayout.addView(createButton("15. QuickSetupAddressBarPosition") {
            try {
                QuickSetupAddressBarPositionBottomSheet.newInstance(
                    initialSelection = OmnibarType.SINGLE_TOP,
                    showSplitOption = false,
                ).show(supportFragmentManager, "devtool_15")
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Button 16: QuickSetupSearchOptionsBottomSheet
        linearLayout.addView(createButton("16. QuickSetupSearchOptions") {
            try {
                QuickSetupSearchOptionsBottomSheet.newInstance(initialWithAi = true)
                    .show(supportFragmentManager, "devtool_16")
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Button 17: RemoveWidgetInstructionsBottomSheet
        linearLayout.addView(createButton("17. RemoveWidgetInstructions") {
            try {
                RemoveWidgetInstructionsBottomSheet()
                    .show(supportFragmentManager, "devtool_17")
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        // Button 18: DefaultBrowserBottomSheetDialog
        linearLayout.addView(createButton("18. DefaultBrowserBottomSheetDialog") {
            try {
                DefaultBrowserBottomSheetDialog(
                    context = this,
                    edgeToEdgeProvider = edgeToEdgeProvider,
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        })

        scrollView.addView(linearLayout)
        setContentView(scrollView)
    }

    private fun createButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            setOnClickListener { onClick() }
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, BottomSheetDevToolActivity::class.java)
        }
    }
}
