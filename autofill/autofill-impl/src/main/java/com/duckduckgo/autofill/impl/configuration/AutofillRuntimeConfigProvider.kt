/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.impl.configuration

import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.email.incontext.availability.EmailProtectionInContextAvailabilityRules
import com.duckduckgo.autofill.impl.store.NeverSavedSiteRepository
import com.duckduckgo.autofill.impl.store.ReAuthenticationDetails
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

interface AutofillRuntimeConfigProvider {
    /**
     * Populates [rawJs] with the runtime configuration the autofill script needs for [url].
     *
     * The returned string is already prefixed with the `javascript:` scheme, so it can be handed straight to
     * `WebView.evaluateJavascript`. The prefix is applied while the script is assembled rather than by the caller
     * because [rawJs] is close to a megabyte: prepending it afterwards would copy the whole script a second time,
     * on every page load. Callers must not add the prefix again.
     */
    suspend fun getRuntimeConfiguration(
        rawJs: String,
        url: String?,
        reAuthenticationDetails: ReAuthenticationDetails,
        browserMode: BrowserMode,
    ): String
}

@ContributesBinding(AppScope::class)
class RealAutofillRuntimeConfigProvider @Inject constructor(
    private val runtimeConfigurationWriter: RuntimeConfigurationWriter,
    private val autofillCapabilityChecker: AutofillCapabilityChecker,
    private val autofillFeature: AutofillFeature,
    private val emailProtectionInContextAvailabilityRules: EmailProtectionInContextAvailabilityRules,
    private val neverSavedSiteRepository: NeverSavedSiteRepository,
    private val siteSpecificFixesStore: AutofillSiteSpecificFixesStore,
    private val autofillAvailableInputTypesProvider: AutofillAvailableInputTypesProvider,
) : AutofillRuntimeConfigProvider {
    override suspend fun getRuntimeConfiguration(
        rawJs: String,
        url: String?,
        reAuthenticationDetails: ReAuthenticationDetails,
        browserMode: BrowserMode,
    ): String {
        logcat(VERBOSE) { "BrowserAutofill: getRuntimeConfiguration called" }

        val contentScope = runtimeConfigurationWriter.generateContentScope(siteSpecificFixesStore.getConfig())
        val userUnprotectedDomains = runtimeConfigurationWriter.generateUserUnprotectedDomains()
        val userPreferences = runtimeConfigurationWriter.generateUserPreferences(
            autofillCredentials = canInjectCredentials(url),
            credentialSaving = canSaveCredentials(url),
            passwordGeneration = canGeneratePasswords(url),
            showInlineKeyIcon = true,
            showInContextEmailProtectionSignup = canShowInContextEmailProtectionSignup(url),
            unknownUsernameCategorization = canCategorizeUnknownUsername(),
            canCategorizePasswordVariant = canCategorizePasswordVariant(),
            partialFormSaves = partialFormSaves(),
        ).also {
            logcat(VERBOSE) { "autofill-config: userPreferences for $url: \n$it" }
        }
        val availableInputTypes = generateAvailableInputTypes(url, reAuthenticationDetails, browserMode)

        return rawJs.replacePlaceholders(
            prefix = JAVASCRIPT_SCHEME_PREFIX,
            TAG_INJECT_CONTENT_SCOPE to contentScope,
            TAG_INJECT_USER_UNPROTECTED_DOMAINS to userUnprotectedDomains,
            TAG_INJECT_USER_PREFERENCES to userPreferences,
            TAG_INJECT_AVAILABLE_INPUT_TYPES to availableInputTypes,
        )
    }

    private fun String.replacePlaceholders(prefix: String, vararg replacements: Pair<String, String>): String {
        val matches = replacements
            .mapNotNull { (placeholder, replacement) ->
                val index = indexOf(placeholder)
                if (index == -1) null else PlaceholderMatch(index, placeholder.length, replacement)
            }
            .sortedBy { it.index }

        val capacity = prefix.length + length + matches.sumOf { it.replacement.length - it.placeholderLength }
        val builder = StringBuilder(capacity).append(prefix)
        var cursor = 0
        matches.forEach { match ->
            builder.append(this, cursor, match.index).append(match.replacement)
            cursor = match.index + match.placeholderLength
        }
        return builder.append(this, cursor, length).toString()
    }

    private class PlaceholderMatch(
        val index: Int,
        val placeholderLength: Int,
        val replacement: String,
    )

    private suspend fun generateAvailableInputTypes(
        url: String?,
        reAuthenticationDetails: ReAuthenticationDetails,
        browserMode: BrowserMode,
    ): String {
        val inputTypes = autofillAvailableInputTypesProvider.getTypes(url, reAuthenticationDetails, browserMode)

        val json = runtimeConfigurationWriter.generateResponseGetAvailableInputTypes(inputTypes).also {
            logcat(VERBOSE) { "autofill-config: availableInputTypes for $url: \n$it" }
        }
        return "availableInputTypes = $json"
    }

    private suspend fun canInjectCredentials(url: String?): Boolean {
        if (url == null) return false
        return autofillCapabilityChecker.canInjectCredentialsToWebView(url)
    }

    private suspend fun canSaveCredentials(url: String?): Boolean {
        if (url == null) return false

        /*
         * if site is in "never save" list, we don't want to offer to save credentials for it, however we deliberately don't check that here.
         * we handle checking the "never save" the callback for storing credentials, so that we can suppress the system password manager prompt.
         */

        return autofillCapabilityChecker.canSaveCredentialsFromWebView(url)
    }

    private suspend fun canGeneratePasswords(url: String?): Boolean {
        if (url == null) return false
        if (!autofillCapabilityChecker.canGeneratePasswordFromWebView(url)) {
            return false
        }

        /*
         * if site is in "never save" list, as well as not offering to save we also don't want to offer generated passwords for it
         * unlike in [canSaveCredentials], we do check this here, because we need to inform the JS not to show the icon for generating passwords
         */
        return !neverSavedSiteRepository.isInNeverSaveList(url)
    }

    private fun canCategorizeUnknownUsername(): Boolean {
        return autofillFeature.canCategorizeUnknownUsername().isEnabled()
    }

    private fun canCategorizePasswordVariant(): Boolean {
        return autofillFeature.passwordVariantCategorization().isEnabled()
    }

    private fun partialFormSaves(): Boolean {
        return autofillFeature.partialFormSaves().isEnabled()
    }

    private suspend fun canShowInContextEmailProtectionSignup(url: String?): Boolean {
        if (url == null) return false
        return emailProtectionInContextAvailabilityRules.permittedToShow(url)
    }

    companion object {
        private const val JAVASCRIPT_SCHEME_PREFIX = "javascript:"
        private const val TAG_INJECT_CONTENT_SCOPE = "// INJECT contentScope HERE"
        private const val TAG_INJECT_USER_UNPROTECTED_DOMAINS = "// INJECT userUnprotectedDomains HERE"
        private const val TAG_INJECT_USER_PREFERENCES = "// INJECT userPreferences HERE"
        private const val TAG_INJECT_AVAILABLE_INPUT_TYPES = "// INJECT availableInputTypes HERE"
    }
}
