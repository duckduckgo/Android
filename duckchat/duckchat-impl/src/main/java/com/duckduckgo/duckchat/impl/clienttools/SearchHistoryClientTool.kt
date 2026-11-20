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

package com.duckduckgo.duckchat.impl.clienttools

import androidx.core.net.toUri
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.history.api.NavigationHistory
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@ContributesIntoSet(AppScope::class)
class SearchHistoryClientTool @Inject constructor(
    private val feature: AiChatBrowserToolsFeature,
    private val navigationHistory: NavigationHistory,
) : ClientTool {

    override val name: String = "searchHistory"
    override val version: Int = 1
    override val permissionMode: ClientToolPermissionMode = ClientToolPermissionMode.Ask(
        promptTitle = R.string.duckAiClientToolSearchHistoryPromptTitle,
        promptMessage = R.string.duckAiClientToolSearchHistoryPromptMessage,
        name = R.string.duckAiClientToolSearchHistoryName,
    )

    override val settingsListing = ClientToolSettingsListing(
        section = ClientToolSettingsSection.SEE,
        name = R.string.duckAiClientToolSearchHistoryName,
        description = R.string.duckAiClientToolSearchHistoryDescription,
        icon = CommonR.drawable.ic_history_24,
    )

    override fun isEnabled(): Boolean = feature.self().isEnabled() && feature.searchHistory().isEnabled()

    override fun isValidInput(input: JSONObject): Boolean = parse(input) != null

    override suspend fun execute(
        input: JSONObject,
        context: ClientToolCallContext,
    ): ClientToolResult {
        val request = parse(input) ?: return ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS)
        if (!navigationHistory.isHistoryFeatureAvailable() || !navigationHistory.isHistoryUserEnabled()) {
            return ClientToolResult.Failure(ClientToolErrorCode.UNAVAILABLE)
        }

        val pages = pagesVisitedIn(request.range)
        val ranked = if (request.words.isEmpty()) {
            pages.sortedByDescending { it.latestVisit }
        } else {
            val needed = (request.words.size + 1) / 2
            pages.map { it to score(it, request.words) }
                .filter { (_, score) -> score.wordsMatched >= needed }
                .sortedWith(
                    compareByDescending<Pair<Page, Score>> { (_, score) -> score.wordsMatched }
                        .thenByDescending { (_, score) -> score.quality }
                        .thenByDescending { (page, _) -> page.daysVisited }
                        .thenByDescending { (page, _) -> page.latestVisit },
                )
                .map { (page, _) -> page }
        }

        val results = JSONArray()
        ranked.take(request.limit).forEach { page ->
            results.put(
                JSONObject()
                    .put(TITLE, ClientToolOutput.title(page.title))
                    .put(URL, page.url)
                    .put(VISITED_AT, ClientToolOutput.timestamp(page.latestVisit)),
            )
        }
        return ClientToolResult.Success(JSONObject().put(RESULTS, results))
    }

    /** One page per URL as the contract shows it, so addresses that differ only by query or fragment merge. */
    private suspend fun pagesVisitedIn(range: VisitRange): List<Page> {
        return navigationHistory.getHistory().firstOrNull().orEmpty()
            .filter { it.url.toString().isHttpOrHttps() }
            .flatMap { entry ->
                val url = ClientToolOutput.url(entry.url.toString())
                entry.visits.map { ClientToolOutput.instantOf(it) }.filter { it in range }.map { Visit(url, entry.title, it) }
            }
            .groupBy { it.url }
            .map { (url, visits) ->
                val latest = visits.maxBy { it.at }
                Page(
                    url = url,
                    title = latest.title,
                    latestVisit = latest.at,
                    daysVisited = visits.map { it.at.atZone(ZoneOffset.UTC).toLocalDate() }.distinct().size,
                )
            }
    }

    private fun score(
        page: Page,
        words: List<String>,
    ): Score {
        val uri = page.url.toUri()
        val fields = listOf(page.title, uri.host.orEmpty(), uri.path.orEmpty()).map { it.lowercase(Locale.ROOT) }
        val qualities = words.map { word -> fields.maxOf { quality(it, word) } }
        return Score(wordsMatched = qualities.count { it > 0 }, quality = qualities.sum())
    }

    /** 2 when [word] starts a token of [field], 1 when it only appears inside one, 0 when absent. */
    private fun quality(
        field: String,
        word: String,
    ): Int {
        var index = field.indexOf(word)
        if (index < 0) return 0
        while (index >= 0) {
            if (index == 0 || !field[index - 1].isLetterOrDigit()) return 2
            index = field.indexOf(word, index + 1)
        }
        return 1
    }

    private fun parse(input: JSONObject): Request? {
        if (!ClientToolInput.hasOnlyKeys(input, QUERY, START_DATE, END_DATE, LIMIT)) return null
        val query = if (input.has(QUERY)) ClientToolInput.string(input, QUERY, lengths = 0..MAX_QUERY_LENGTH) ?: return null else ""
        val start = if (input.has(START_DATE)) bound(ClientToolInput.string(input, START_DATE), endOfDay = false) ?: return null else null
        val end = if (input.has(END_DATE)) bound(ClientToolInput.string(input, END_DATE), endOfDay = true) ?: return null else null
        if (start != null && end != null && start > end) return null
        val limit = ClientToolInput.limit(input, LIMIT, default = DEFAULT_LIMIT, max = MAX_LIMIT) ?: return null
        val words = query.lowercase(Locale.ROOT).split(WHITESPACE).filter { it.isNotEmpty() }.distinct()
        return Request(words, VisitRange(start, end), limit)
    }

    /** A date-only bound covers the whole UTC day; a date and time without an offset is read as UTC. */
    private fun bound(
        value: String?,
        endOfDay: Boolean,
    ): Instant? {
        value ?: return null
        parseOrNull { LocalDate.parse(value) }?.let { date ->
            val day = if (endOfDay) date.plusDays(1) else date
            val start = day.atStartOfDay(ZoneOffset.UTC).toInstant()
            return if (endOfDay) start.minusNanos(1) else start
        }
        return parseOrNull { OffsetDateTime.parse(value).toInstant() }
            ?: parseOrNull { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }
    }

    private inline fun <T> parseOrNull(parse: () -> T): T? = try {
        parse()
    } catch (e: DateTimeParseException) {
        null
    }

    private data class Request(
        val words: List<String>,
        val range: VisitRange,
        val limit: Int,
    )

    private data class VisitRange(
        val start: Instant?,
        val end: Instant?,
    ) {
        operator fun contains(instant: Instant): Boolean = (start == null || instant >= start) && (end == null || instant <= end)
    }

    private data class Visit(
        val url: String,
        val title: String,
        val at: Instant,
    )

    private data class Page(
        val url: String,
        val title: String,
        val latestVisit: Instant,
        val daysVisited: Int,
    )

    private data class Score(
        val wordsMatched: Int,
        val quality: Int,
    )

    private companion object {
        const val QUERY = "query"
        const val START_DATE = "startDate"
        const val END_DATE = "endDate"
        const val LIMIT = "limit"
        const val RESULTS = "results"
        const val TITLE = "title"
        const val URL = "url"
        const val VISITED_AT = "visitedAt"
        const val DEFAULT_LIMIT = 10
        const val MAX_LIMIT = 50
        const val MAX_QUERY_LENGTH = 1000
        val WHITESPACE = Regex("\\s+")
    }
}
