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

package com.duckduckgo.autofill.impl.email

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.Fragment
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.EmailProtectionChooserDialog
import com.duckduckgo.autofill.api.EmailProtectionChooserDialog.UseEmailResultType
import com.duckduckgo.autofill.api.EmailProtectionChooserDialog.UseEmailResultType.*
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import timber.log.Timber

@ContributesMultibinding(AppScope::class)
class ResultHandlerEmailProtectionChooseEmail @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : AutofillFragmentResultsPlugin {

    override fun processResult(
        result: Bundle,
        context: Context,
        tabId: String,
        fragment: Fragment,
        autofillCallback: AutofillEventListener,
    ) {
        Timber.d("${this::class.java.simpleName}: processing result")

        val userSelection: UseEmailResultType =
            result.safeGetParcelable(EmailProtectionChooserDialog.KEY_RESULT) ?: return
        val originalUrl = result.getString(EmailProtectionChooserDialog.KEY_URL) ?: return

        when (userSelection) {
            UsePersonalEmailAddress -> autofillCallback.onUseEmailProtectionPersonalAddress(originalUrl)
            UsePrivateAliasAddress -> autofillCallback.onUseEmailProtectionPrivateAlias(originalUrl)
            DoNotUseEmailProtection -> autofillCallback.onRejectToUseEmailProtection(originalUrl)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("NewApi")
    private inline fun <reified T : Parcelable> Bundle.safeGetParcelable(key: String) =
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            getParcelable(key, T::class.java)
        } else {
            getParcelable(key)
        }

    override fun resultKey(tabId: String): String {
        return EmailProtectionChooserDialog.resultKey(tabId)
    }
}
