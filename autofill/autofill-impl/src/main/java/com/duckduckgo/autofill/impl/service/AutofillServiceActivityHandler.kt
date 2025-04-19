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

import android.app.Activity
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.service.autofill.Dataset
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.service.AutofillFieldType.UNKNOWN
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AutofillServiceActivityHandler {
    fun onFillRequest(
        activity: Activity,
        credential: LoginCredentials,
        assistStructure: AssistStructure,
    )
}

@ContributesBinding(ActivityScope::class)
class RealAutofillServiceIntentHelper @Inject constructor(
    private val autofillParser: AutofillParser,
    private val suggestionsFormatter: AutofillServiceSuggestionCredentialFormatter,
    private val viewProvider: AutofillServiceViewProvider,
) : AutofillServiceActivityHandler {

    override fun onFillRequest(
        activity: Activity,
        credential: LoginCredentials,
        assistStructure: AssistStructure,
    ) {
        val parsedNodes = autofillParser.parseStructure(assistStructure)
        val detectedNode: AutofillRootNode = parsedNodes.findBestFillableNode() ?: return
        val fields = detectedNode.parsedAutofillFields.filter { it.type != UNKNOWN }
        val dataset = buildDataset(activity, fields, credential)
        val resultIntent = Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
        }
        activity.setResult(Activity.RESULT_OK, resultIntent)
        activity.finish()
    }

    private fun buildDataset(
        context: Context,
        fields: List<ParsedAutofillField>,
        credential: LoginCredentials,
    ): Dataset {
        val datasetBuilder = Dataset.Builder()
        fields.forEach { fieldsToAutofill ->
            val suggestionUISpecs = suggestionsFormatter.getSuggestionSpecs(credential)
            val formPresentation = viewProvider.createFormPresentation(
                context,
                suggestionUISpecs.title,
                suggestionUISpecs.subtitle,
                suggestionUISpecs.icon,
            )
            datasetBuilder.setValue(
                fieldsToAutofill.autofillId,
                if (fieldsToAutofill.type == AutofillFieldType.USERNAME) {
                    AutofillValue.forText(credential.username)
                } else {
                    AutofillValue.forText(credential.password)
                },
                formPresentation,
            )
        }

        return datasetBuilder.build()
    }
}
