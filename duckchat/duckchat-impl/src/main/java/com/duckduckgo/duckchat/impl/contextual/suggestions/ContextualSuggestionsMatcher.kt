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

package com.duckduckgo.duckchat.impl.contextual.suggestions

import java.net.URI
import java.util.Locale

data class ContextualSuggestedPrompt(
    val id: String,
    val label: String,
    val prompt: String,
    val icon: String?,
)

data class ResolvedPageSuggestions(
    val suggestions: List<ContextualSuggestedPrompt>,
    val isSmart: Boolean,
    val pageType: SuggestionsPageType,
)

enum class SuggestionsPageType {
    RECIPE, PRODUCT, ARTICLE, VIDEO, JOB, BOOK, EVENT, PLACE, FORUM, CODE, COURSE, REVIEW, PERSON, HOWTO, FAQ, NONE;
    val pixelValue: String get() = name.lowercase()
}

data class PageTypeSignals(
    val jsonLdType: List<String> = emptyList(),
    val ogType: String? = null,
    val lang: String = "",
)

data class ResolvePageSuggestionsInput(
    val pageTypeSignals: PageTypeSignals?,
    val url: String?,
    val uiLocale: String,
)

data class SuggestionCatalog(
    val maxSuggestedPrompts: Int,
    val defaults: List<String>,
    val catalog: Map<String, Entry>,
    val byJsonLdType: List<JsonLdMapping>,
    val byOgType: Map<String, List<String>>,
    val byDomain: Map<String, List<String>>,
) {
    data class Entry(
        val label: String,
        val icon: String? = null,
        val prompt: String,
        val condition: String? = null,
    )

    data class JsonLdMapping(
        val type: String,
        val ids: List<String>,
    )
}

object ContextualSuggestionsMatcher {

    private const val LANGUAGE_TEMPLATE = "{language}"
    private const val LANGUAGE_FORMAT_ARG = "%1\$s"
    private const val CONDITION_DIFFERENT_LANGUAGE = "differentLanguage"

    fun resolve(
        input: ResolvePageSuggestionsInput,
        catalog: SuggestionCatalog,
    ): ResolvedPageSuggestions {
        val cap = maxOf(1, catalog.maxSuggestedPrompts)
        val (candidateIds, isSmart) = collectCandidateIds(input, catalog, cap)
        val seen = mutableSetOf<String>()
        val resolved = mutableListOf<ContextualSuggestedPrompt>()

        for (id in candidateIds) {
            if (resolved.size >= cap) break
            if (!seen.add(id)) continue

            val entry = catalog.catalog[id] ?: continue
            if (!conditionPasses(entry.condition, input)) continue

            resolved.add(
                ContextualSuggestedPrompt(
                    id = id,
                    label = entry.label,
                    prompt = applyTemplate(entry.prompt, input),
                    icon = entry.icon,
                ),
            )
        }
        return ResolvedPageSuggestions(
            suggestions = resolved,
            isSmart = isSmart,
            pageType = classifyPageType(input.pageTypeSignals),
        )
    }

    fun classifyPageType(signals: PageTypeSignals?): SuggestionsPageType {
        if (signals == null) return SuggestionsPageType.NONE
        for (type in signals.jsonLdType) {
            jsonLdTypeToPageType[type.trim().lowercase()]?.let { return it }
        }
        signals.ogType?.trim()?.lowercase()?.let { ogType ->
            ogTypeToPageType[ogType]?.let { return it }
        }
        return SuggestionsPageType.NONE
    }

    private fun collectCandidateIds(
        input: ResolvePageSuggestionsInput,
        catalog: SuggestionCatalog,
        cap: Int,
    ): Pair<List<String>, Boolean> {
        var contextual: List<String>? = null

        input.pageTypeSignals?.let { signals ->
            contextual = matchByJsonLdType(signals.jsonLdType, catalog.byJsonLdType)
            if (contextual == null) {
                val ogType = signals.ogType
                if (!ogType.isNullOrEmpty()) {
                    contextual = catalog.byOgType[ogType.trim().lowercase()]
                }
            }
        }
        if (contextual == null) {
            hostname(input.url)?.let { host ->
                contextual = matchByDomain(host, catalog.byDomain)
            }
        }

        val priorityDefaults = catalog.defaults.filter { id ->
            val condition = catalog.catalog[id]?.condition ?: return@filter false
            conditionPasses(condition, input)
        }
        val floorDefaults = catalog.defaults.filter { catalog.catalog[it]?.condition == null }

        val body = contextual ?: floorDefaults
        val bodyBudget = (cap - priorityDefaults.size).coerceAtLeast(0)
        return body.take(bodyBudget) + priorityDefaults to (contextual != null)
    }

    private val jsonLdTypeToPageType = mapOf(
        "recipe" to SuggestionsPageType.RECIPE,
        "product" to SuggestionsPageType.PRODUCT,
        "article" to SuggestionsPageType.ARTICLE,
        "newsarticle" to SuggestionsPageType.ARTICLE,
        "reportagenewsarticle" to SuggestionsPageType.ARTICLE,
        "liveblogposting" to SuggestionsPageType.ARTICLE,
        "blogposting" to SuggestionsPageType.ARTICLE,
        "scholarlyarticle" to SuggestionsPageType.ARTICLE,
        "videoobject" to SuggestionsPageType.VIDEO,
        "movie" to SuggestionsPageType.VIDEO,
        "tvseries" to SuggestionsPageType.VIDEO,
        "jobposting" to SuggestionsPageType.JOB,
        "book" to SuggestionsPageType.BOOK,
        "course" to SuggestionsPageType.COURSE,
        "event" to SuggestionsPageType.EVENT,
        "restaurant" to SuggestionsPageType.PLACE,
        "foodestablishment" to SuggestionsPageType.PLACE,
        "localbusiness" to SuggestionsPageType.PLACE,
        "discussionforumposting" to SuggestionsPageType.FORUM,
        "qapage" to SuggestionsPageType.FORUM,
        "faqpage" to SuggestionsPageType.FAQ,
        "softwaresourcecode" to SuggestionsPageType.CODE,
        "review" to SuggestionsPageType.REVIEW,
        "aggregaterating" to SuggestionsPageType.REVIEW,
        "person" to SuggestionsPageType.PERSON,
        "howto" to SuggestionsPageType.HOWTO,
    )

    private val ogTypeToPageType = mapOf(
        "article" to SuggestionsPageType.ARTICLE,
        "product" to SuggestionsPageType.PRODUCT,
        "product.item" to SuggestionsPageType.PRODUCT,
        "video" to SuggestionsPageType.VIDEO,
        "video.other" to SuggestionsPageType.VIDEO,
        "video.movie" to SuggestionsPageType.VIDEO,
        "video.episode" to SuggestionsPageType.VIDEO,
        "video.tv_show" to SuggestionsPageType.VIDEO,
        "book" to SuggestionsPageType.BOOK,
        "profile" to SuggestionsPageType.PERSON,
    )

    private fun matchByJsonLdType(
        types: List<String>,
        mappings: List<SuggestionCatalog.JsonLdMapping>,
    ): List<String>? {
        if (types.isEmpty()) return null
        val present = types.map { it.trim().lowercase() }.toSet()
        return mappings.firstOrNull { present.contains(it.type.lowercase()) }?.ids
    }

    private fun matchByDomain(
        hostname: String,
        table: Map<String, List<String>>,
    ): List<String>? {
        return table.entries.firstOrNull { domainMatches(hostname, it.key) }?.value
    }

    private fun conditionPasses(
        condition: String?,
        input: ResolvePageSuggestionsInput,
    ): Boolean {
        if (condition == null) return true
        return when (condition) {
            CONDITION_DIFFERENT_LANGUAGE -> {
                val pageLang = pageLanguageSubtag(input.pageTypeSignals?.lang ?: "")
                val uiLang = uiLanguageSubtag(input.uiLocale)
                pageLang.isNotEmpty() && uiLang.isNotEmpty() && pageLang != uiLang
            }
            else -> false
        }
    }

    internal fun applyTemplate(
        prompt: String,
        input: ResolvePageSuggestionsInput,
    ): String {
        if (prompt.contains(LANGUAGE_TEMPLATE)) return prompt.replace(LANGUAGE_TEMPLATE, languageDisplayName(input.uiLocale))
        if (prompt.contains(LANGUAGE_FORMAT_ARG)) return prompt.replace(LANGUAGE_FORMAT_ARG, languageDisplayName(input.uiLocale))
        return prompt
    }

    private fun pageLanguageSubtag(tag: String): String {
        return tag.trim().lowercase().split("-").firstOrNull().orEmpty()
    }

    private fun uiLanguageSubtag(uiLocale: String): String {
        return localeFrom(uiLocale).language.lowercase()
    }

    private fun languageDisplayName(uiLocale: String): String {
        val subtag = uiLanguageSubtag(uiLocale)
        if (subtag.isEmpty()) return subtag
        val name = Locale(subtag).getDisplayLanguage(localeFrom(uiLocale))
        return if (name.isNotEmpty() && name.lowercase() != subtag) name else subtag
    }

    private fun localeFrom(uiLocale: String): Locale {
        val tag = uiLocale.substringBefore("@").replace("_", "-")
        return Locale.forLanguageTag(tag)
    }

    private fun hostname(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val host = runCatching { URI(url).host }.getOrNull() ?: return null
        return host.lowercase()
    }

    private fun domainMatches(
        hostname: String,
        domain: String,
    ): Boolean {
        val normalized = domain.trim().lowercase()
        return hostname == normalized || hostname.endsWith(".$normalized")
    }
}
