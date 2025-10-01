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

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.autofill.Dataset
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import androidx.annotation.RequiresApi
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.service.AutofillFieldType.UNKNOWN
import com.duckduckgo.autofill.impl.service.AutofillFieldType.USERNAME
import com.duckduckgo.autofill.impl.service.AutofillProviderChooseActivity.Companion.FILL_REQUEST_AUTOFILL_CREDENTIAL_ID_EXTRAS
import com.duckduckgo.autofill.impl.service.AutofillProviderChooseActivity.Companion.FILL_REQUEST_AUTOFILL_ID_EXTRAS
import com.duckduckgo.autofill.impl.service.AutofillProviderChooseActivity.Companion.FILL_REQUEST_PACKAGE_ID_EXTRAS
import com.duckduckgo.autofill.impl.service.AutofillProviderChooseActivity.Companion.FILL_REQUEST_URL_EXTRAS
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlin.random.Random
import logcat.LogPriority.INFO
import logcat.logcat

interface AutofillProviderSuggestions {
    suspend fun buildSuggestionsResponse(
        context: Context,
        nodeToAutofill: AutofillRootNode,
        request: FillRequest,
    ): FillResponse
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAutofillProviderSuggestions @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val viewProvider: AutofillServiceViewProvider,
    private val suggestionsFormatter: AutofillServiceSuggestionCredentialFormatter,
    private val autofillSuggestions: AutofillSuggestions,
) : AutofillProviderSuggestions {

    companion object {
        private const val RESERVED_SUGGESTIONS_SIZE = 1
    }

    @SuppressLint("NewApi")
    override suspend fun buildSuggestionsResponse(
        context: Context,
        nodeToAutofill: AutofillRootNode,
        request: FillRequest,
    ): FillResponse {
        val fillableFields = nodeToAutofill.parsedAutofillFields.filter { it.type != UNKNOWN }
        logcat(INFO) {
            "DDGAutofillService Fillable Request for rootNode: ${nodeToAutofill.website} and ${nodeToAutofill.packageId} for fields:\n${
                fillableFields.joinToString(
                    separator = "\n",
                )
            }"
        }

        val response = FillResponse.Builder()
        // We add credential suggestions
        fillableFields.forEach { fieldsToAutofill ->
            var inlineSuggestionsToShow = getMaxInlinedSuggestions(request) - RESERVED_SUGGESTIONS_SIZE
            val credentials = loginCredentials(nodeToAutofill)
            logcat(INFO) {
                """
                    DDGAutofillService suggesting credentials for ${fieldsToAutofill.autofillId}credentials:
                    ${credentials.joinToString(separator = "\n")}
                """.trimIndent()
            }
            credentials?.forEach { credential ->
                val datasetBuilder = Dataset.Builder()
                val suggestionUISpecs = suggestionsFormatter.getSuggestionSpecs(credential)
                val pendingIntent = createNewAutofillSelectionIntent(context, fieldsToAutofill.autofillId, credential.id)
                // >= android 11 inline presentations are supported
                if (appBuildConfig.sdkInt >= 30 && inlineSuggestionsToShow > 0) {
                    datasetBuilder.addInlinePresentationsIfSupported(
                        context,
                        request,
                        suggestionUISpecs.title,
                        suggestionUISpecs.subtitle,
                        suggestionUISpecs.icon,
                        pendingIntent,
                    )
                    inlineSuggestionsToShow -= 1
                }
                val formPresentation = viewProvider.createFormPresentation(
                    context,
                    suggestionUISpecs.title,
                    suggestionUISpecs.subtitle,
                    suggestionUISpecs.icon,
                )
                logcat(INFO) {
                    "DDGAutofillService adding suggestion: ${fieldsToAutofill.autofillId}-${fieldsToAutofill.type} with ${suggestionUISpecs.title}"
                }
                datasetBuilder.setValue(
                    fieldsToAutofill.autofillId,
                    autofillValue(credential, fieldsToAutofill.type),
                    formPresentation,
                )
                val dataset = datasetBuilder
                    .setAuthentication(pendingIntent.intentSender)
                    .build()
                response.addDataset(dataset)
            }
        }

        // Last suggestion to open DDG App and manually choose a credential
        val ddgAppDataSetBuild = createAccessDDGDataSet(
            context,
            request,
            fillableFields,
            nodeToAutofill.website.orEmpty(),
            nodeToAutofill.packageId.orEmpty(),
        )
        logcat(INFO) { "DDGAutofillService adding suggestion DuckDuckGo Search" }
        response.addDataset(ddgAppDataSetBuild)

        val unknownIds = nodeToAutofill.parsedAutofillFields.filter { it.type == UNKNOWN }.map { it.autofillId }
        response.setIgnoredIds(*unknownIds.toTypedArray())
        return response.build()
    }

    @SuppressLint("NewApi")
    private fun createAccessDDGDataSet(
        context: Context,
        request: FillRequest,
        fillableFields: List<ParsedAutofillField>,
        url: String,
        packageId: String,
    ): Dataset {
        // add access passwords
        val ddgAppDataSet = Dataset.Builder()
        val specs = suggestionsFormatter.getOpenDuckDuckGoSuggestionSpecs()
        val pendingIntent = createAutofillSelectionIntent(context, url, packageId)
        if (appBuildConfig.sdkInt >= 30) {
            ddgAppDataSet.addInlinePresentationsIfSupported(context, request, specs.title, specs.subtitle, specs.icon, pendingIntent)
        }
        val formPresentation = viewProvider.createFormPresentation(context, specs.title, specs.subtitle, specs.icon)
        fillableFields.forEach { fieldsToAutofill ->
            ddgAppDataSet.setValue(
                fieldsToAutofill.autofillId,
                AutofillValue.forText("placeholder"),
                formPresentation,
            )
        }
        val ddgAppDataSetBuild = ddgAppDataSet
            .setAuthentication(pendingIntent.intentSender)
            .build()
        return ddgAppDataSetBuild
    }

    @RequiresApi(30)
    private fun Dataset.Builder.addInlinePresentationsIfSupported(
        context: Context,
        request: FillRequest,
        suggestionTitle: String,
        suggestionSubtitle: String,
        icon: Int,
        attribution: PendingIntent,
    ) {
        val inlinePresentationSpec = request.inlineSuggestionsRequest?.inlinePresentationSpecs?.firstOrNull() ?: return
        viewProvider.createInlinePresentation(
            context,
            attribution,
            suggestionTitle,
            suggestionSubtitle,
            icon,
            inlinePresentationSpec,
        )?.let { inlinePresentation ->
            logcat(INFO) { "DDGAutofillService adding inlinePresentation for suggestion: $suggestionTitle" }
            this.setInlinePresentation(inlinePresentation)
        }
    }

    @SuppressLint("NewApi")
    private fun getMaxInlinedSuggestions(
        request: FillRequest,
    ): Int {
        if (appBuildConfig.sdkInt >= 30) {
            return request.inlineSuggestionsRequest?.maxSuggestionCount ?: 0
        }
        return 0
    }

    private fun autofillValue(
        credential: LoginCredentials?,
        autofillRequestedType: AutofillFieldType,
    ): AutofillValue? = if (autofillRequestedType == USERNAME) {
        AutofillValue.forText(credential?.username ?: "username")
    } else {
        AutofillValue.forText(credential?.password ?: "password")
    }

    private suspend fun loginCredentials(node: AutofillRootNode): List<LoginCredentials> {
        val credentialsForDomain = node.website.takeUnless { it.isNullOrBlank() }?.let { nonEmptyWebsite ->
            autofillSuggestions.getSiteSuggestions(nonEmptyWebsite)
        } ?: emptyList()

        val credentialsForPackage = node.packageId.takeUnless { it.isNullOrBlank() }?.let { nonEmptyPackageId ->
            autofillSuggestions.getAppSuggestions(nonEmptyPackageId)
        } ?: emptyList()

        return credentialsForDomain.plus(credentialsForPackage).distinct()
    }

    private fun createAutofillSelectionIntent(context: Context, url: String, packageId: String): PendingIntent {
        val intent = Intent(context, AutofillProviderChooseActivity::class.java)
        intent.putExtra(FILL_REQUEST_URL_EXTRAS, url)
        intent.putExtra(FILL_REQUEST_PACKAGE_ID_EXTRAS, packageId)
        return PendingIntent
            .getActivity(
                context,
                Random.nextInt(),
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
    }

    private fun createNewAutofillSelectionIntent(
        context: Context,
        autofillId: AutofillId,
        credentialId: Long?,
    ): PendingIntent {
        val intent = Intent(context, AutofillProviderFillSuggestionActivity::class.java)
        intent.putExtra(FILL_REQUEST_AUTOFILL_ID_EXTRAS, autofillId)
        intent.putExtra(FILL_REQUEST_AUTOFILL_CREDENTIAL_ID_EXTRAS, credentialId)
        return PendingIntent
            .getActivity(
                context,
                Random.nextInt(), // Different request code avoids overriding previous intents.
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
    }
}
