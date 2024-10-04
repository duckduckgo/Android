/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.autofill.api.promotion.PasswordsScreenPromotionPlugin
import com.duckduckgo.autofill.api.promotion.PasswordsScreenPromotionPlugin.Companion.PRIORITY_KEY_AUTOFILL_SUPPORT_WARNING
import com.duckduckgo.autofill.impl.databinding.ViewAutofillWarningSupportBinding
import com.duckduckgo.autofill.impl.ui.credential.management.survey.AutofillSurvey
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

@ContributesMultibinding(scope = AppScope::class)
@PriorityKey(PRIORITY_KEY_AUTOFILL_SUPPORT_WARNING)
class AutofillSupportWarningUI @Inject constructor(
    private val internalAutofillCapabilityChecker: InternalAutofillCapabilityChecker,
    private val autofillSurvey: AutofillSurvey,
) : PasswordsScreenPromotionPlugin {

    override suspend fun getView(context: Context, numberSavedPasswords: Int): View? {
        val autofillSupported = internalAutofillCapabilityChecker.webViewSupportsAutofill()
        if (autofillSupported) return null

        return AutofillSupportWarningView(context)
    }
}

@InjectWith(ViewScope::class)
class AutofillSupportWarningView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    private val binding: ViewAutofillWarningSupportBinding by viewBinding()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
        binding.webViewUnsupportedWarningPanel.isVisible = true
    }
}
