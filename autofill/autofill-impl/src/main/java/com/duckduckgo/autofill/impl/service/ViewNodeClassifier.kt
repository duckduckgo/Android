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

package com.duckduckgo.autofill.impl.service

import android.app.assist.AssistStructure.ViewNode
import android.text.InputType
import android.view.View
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import timber.log.Timber

interface ViewNodeClassifier {
    fun classify(viewNode: ViewNode): AutofillFieldType
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AutofillServiceViewNodeClassifier @Inject constructor() : ViewNodeClassifier {
    override fun classify(viewNode: ViewNode): AutofillFieldType {
        val autofillId = viewNode.autofillId
        Timber.i("DDGAutofillService node $autofillId has autofillHints ${viewNode.autofillHints?.joinToString()}")
        Timber.i("DDGAutofillService node $autofillId has options ${viewNode.autofillOptions?.joinToString()}")
        Timber.i("DDGAutofillService node $autofillId has idEntry ${viewNode.idEntry}")
        Timber.i("DDGAutofillService node $autofillId has hints ${viewNode.hint}")
        Timber.i("DDGAutofillService node $autofillId is inputType ${viewNode.inputType and InputType.TYPE_CLASS_TEXT > 0}")
        Timber.i("DDGAutofillService node $autofillId has inputType ${viewNode.inputType}")
        Timber.i("DDGAutofillService node $autofillId has className ${viewNode.className}")
        Timber.i("DDGAutofillService node $autofillId has htmlInfo.attributes ${viewNode.htmlInfo?.attributes?.joinToString()}")

        var autofillType = getType(viewNode.autofillHints)
        if (autofillType == AutofillFieldType.UNKNOWN) {
            if (isTextField(viewNode.inputType)) {
                if (viewNode.idEntry?.containsAny(userNameKeywords) == true ||
                    viewNode.hint?.containsAny(userNameKeywords) == true
                ) {
                    autofillType = AutofillFieldType.USERNAME
                } else if (viewNode.idEntry?.containsAny(passwordKeywords) == true ||
                    viewNode.hint?.containsAny(passwordKeywords) == true
                ) {
                    if (isTextPasswordField(viewNode.inputType)) {
                        autofillType = AutofillFieldType.PASSWORD
                    }
                }
            }

            if (autofillType == AutofillFieldType.UNKNOWN) {
                val isUsername: Boolean = viewNode.htmlInfo?.attributes?.find { it.first == "type" }?.second?.containsAny(userNameKeywords) == true ||
                    viewNode.htmlInfo?.attributes
                    ?.firstOrNull { it.first?.containsAny(listOf("autofill")) == true && it.second?.containsAny(userNameKeywords) == true } != null
                val isPassword = viewNode.htmlInfo?.attributes?.find { it.first == "type" }?.second?.containsAny(passwordKeywords) == true ||
                    viewNode.htmlInfo?.attributes
                    ?.firstOrNull { it.first?.containsAny(listOf("autofill")) == true && it.second?.containsAny(passwordKeywords) == true } != null

                if (isUsername) {
                    autofillType = AutofillFieldType.USERNAME
                } else if (isPassword) {
                    autofillType = AutofillFieldType.PASSWORD
                }
            }
        }

        kotlin.runCatching { viewNode.autofillValue?.textValue?.toString() }.getOrElse {
            // Some views will throw an exception when trying to get the autofill value (e.g: CompoundButton)
            // never try to autofill them
            autofillType = AutofillFieldType.UNKNOWN
        }

        return autofillType
    }

    private fun isTextField(inputType: Int): Boolean {
        return (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
    }

    private fun isTextPasswordField(inputType: Int): Boolean {
        return (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun getType(autofillHints: Array<String>?): AutofillFieldType {
        if (autofillHints == null) return AutofillFieldType.UNKNOWN
        if (autofillHints.any { it in USERNAME_HINTS }) return AutofillFieldType.USERNAME
        if (autofillHints.any { it in PASSWORD_HINTS }) return AutofillFieldType.PASSWORD
        return AutofillFieldType.UNKNOWN
    }

    private fun String.containsAny(words: List<String>): Boolean {
        return words.any { this.contains(it, ignoreCase = true) }
    }

    private val USERNAME_HINTS: List<String> = listOf(
        View.AUTOFILL_HINT_EMAIL_ADDRESS,
        View.AUTOFILL_HINT_USERNAME,
    )

    private val PASSWORD_HINTS: List<String> = listOf(
        View.AUTOFILL_HINT_PASSWORD,
        "passwordAuto",
    )

    private val userNameKeywords = listOf(
        "email",
        "username",
        "user name",
        "identifier",
        "account_name",
    )

    private val passwordKeywords = listOf(
        "password",
    )
}
