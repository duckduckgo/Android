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

package com.duckduckgo.sync.impl.auditlog

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.AccountInfoKeyUnavailableReason
import com.duckduckgo.sync.impl.AuditLogEntry
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.impl.auditlog.AuditLogCheckResult.FailureReason
import com.duckduckgo.sync.store.SyncStore
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

interface AuditLogChecker {
    /**
     * Fetches audit log entries added since the last check and adds logins by other devices to the pending list.
     * The first check for an account only stores a cursor, so it adds nothing. Never throws, except for cancellation.
     */
    suspend fun check(): AuditLogCheckResult
}

sealed interface AuditLogCheckResult {
    data object Disabled : AuditLogCheckResult
    data object NotSignedIn : AuditLogCheckResult
    data object Initialised : AuditLogCheckResult
    data class Checked(val newLogins: Int, val renamedLogins: Int) : AuditLogCheckResult

    /** The account or the stored cursor changed while the check ran, so nothing was written. */
    data object Discarded : AuditLogCheckResult

    /**
     * No new logins were stored; the next check tries again. The cursor is kept, except after a rejected cursor,
     * where it is cleared so the next check starts again with no cursor.
     */
    data class Failed(val reason: FailureReason) : AuditLogCheckResult

    enum class FailureReason {
        RATE_LIMITED,
        AUTH,
        SERVER,
        NETWORK,
        KEY_UNAVAILABLE,
        UNEXPECTED,
    }
}

@ContributesBinding(AppScope::class)
class RealAuditLogChecker @Inject constructor(
    private val syncStore: SyncStore,
    private val syncApi: SyncApi,
    private val loginAuditLogFeature: LoginAuditLogFeature,
    private val stateStore: AuditLogCheckStateStore,
    private val entryDecoder: AuditLogEntryDecoder,
    private val pendingLoginsUpdater: PendingLoginsUpdater,
    private val dispatchers: DispatcherProvider,
) : AuditLogChecker {

    override suspend fun check(): AuditLogCheckResult = withContext(dispatchers.io()) {
        val result = try {
            checkSignedInAccount()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(WARN) { "Sync-AuditLog: check failed with ${e.javaClass.simpleName}" }
            AuditLogCheckResult.Failed(FailureReason.UNEXPECTED)
        }
        logcat { "Sync-AuditLog: check result $result" }
        result
    }

    /** Checks the flag and the account, then runs init if there is no stored cursor, or a normal check if there is. */
    private suspend fun checkSignedInAccount(): AuditLogCheckResult {
        if (!loginAuditLogFeature.isNotificationEnabled()) return AuditLogCheckResult.Disabled

        val token = syncStore.token
        val deviceId = syncStore.deviceId
        if (!syncStore.isSignedIn() || token.isNullOrEmpty() || deviceId.isNullOrEmpty()) return AuditLogCheckResult.NotSignedIn

        val startState = stateStore.read()
        val cursor = startState.cursor ?: return initialise(token, deviceId)
        return checkAfter(token, deviceId, cursor, startState.pendingLogins)
    }

    /** Gets the newest cursor (one entry) and stores it, so only entries after now are checked. Adds no logins. */
    private suspend fun initialise(token: String, deviceId: String): AuditLogCheckResult {
        val after = when (val result = syncApi.getAuditLogLatest(token, limit = 1)) {
            is AuditLogResult.Success -> result.page.after ?: return AuditLogCheckResult.Failed(FailureReason.SERVER)
            else -> return result.toFailure()
        }
        val written = updateIfUnchanged(deviceId, expectedCursor = null) { it.copy(cursor = after) }
        return if (written) AuditLogCheckResult.Initialised else AuditLogCheckResult.Discarded
    }

    /** Clears the rejected cursor, then runs init again at once. */
    private suspend fun restartAfterInvalidCursor(token: String, deviceId: String, cursor: String): AuditLogCheckResult {
        logcat(WARN) { "Sync-AuditLog: cursor rejected, starting again" }
        val cleared = updateIfUnchanged(deviceId, expectedCursor = cursor) { it.copy(cursor = null) }
        return if (cleared) initialise(token, deviceId) else AuditLogCheckResult.Discarded
    }

    /** Fetches every page after [cursor], decodes the entries, and stores the new logins, late names and next cursor. */
    private suspend fun checkAfter(
        token: String,
        deviceId: String,
        cursor: String,
        pendingLogins: List<PendingLogin>,
    ): AuditLogCheckResult {
        val fetched = when (val result = fetchPagesAfter(token, cursor)) {
            is FetchResult.Fetched -> result
            is FetchResult.CursorRejected -> return restartAfterInvalidCursor(token, deviceId, cursor)
            is FetchResult.Failed -> return result.failure
        }

        if (fetched.newestFirst.isEmpty()) {
            return saveCheckResult(PendingLoginsUpdate(newLogins = emptyList(), lateNames = emptyMap()), deviceId, cursor, fetched.nextCursor)
        }

        val decodeResult = entryDecoder.decode(fetched.newestFirst)
        if (decodeResult.keyUnavailableReason in TEMPORARY_KEY_FAILURES) {
            logcat(WARN) { "Sync-AuditLog: account_info key unavailable (${decodeResult.keyUnavailableReason}), keeping cursor" }
            return AuditLogCheckResult.Failed(FailureReason.KEY_UNAVAILABLE)
        }

        val update = pendingLoginsUpdater.prepare(decodeResult.entries, deviceId, pendingLogins)
        return saveCheckResult(update, deviceId, fromCursor = cursor, toCursor = fetched.nextCursor)
    }

    /** Follows the `after` cursor until a page is empty or [MAX_PAGES_PER_CHECK] is reached. Returns the entries newest first. */
    private fun fetchPagesAfter(token: String, cursor: String): FetchResult {
        val pages = mutableListOf<List<AuditLogEntry>>()
        var nextCursor = cursor
        while (pages.size < MAX_PAGES_PER_CHECK) {
            val page = when (val result = syncApi.getAuditLogAfter(token, nextCursor)) {
                is AuditLogResult.Success -> result.page
                is AuditLogResult.InvalidCursor -> return FetchResult.CursorRejected
                else -> return FetchResult.Failed(result.toFailure())
            }
            if (page.entries.isEmpty()) {
                nextCursor = page.after ?: nextCursor
                break
            }
            nextCursor = page.after ?: return FetchResult.Failed(AuditLogCheckResult.Failed(FailureReason.SERVER))
            pages += page.entries
        }
        logcat(VERBOSE) { "Sync-AuditLog: fetched ${pages.sumOf { it.size }} entries in ${pages.size} pages" }
        if (pages.size == MAX_PAGES_PER_CHECK) logcat(INFO) { "Sync-AuditLog: page cap reached, the rest comes on the next check" }
        // Each page is newest first, and each page is newer than the one before it.
        return FetchResult.Fetched(newestFirst = pages.asReversed().flatten(), nextCursor = nextCursor)
    }

    /** Saves a finished check: merges [update] into the pending list and moves the cursor to [toCursor]. Uses [updateIfUnchanged]. */
    private suspend fun saveCheckResult(
        update: PendingLoginsUpdate,
        deviceId: String,
        fromCursor: String,
        toCursor: String,
    ): AuditLogCheckResult {
        var added = 0
        var renamed = 0
        val written = updateIfUnchanged(deviceId, expectedCursor = fromCursor) { state ->
            val applied = update.applyTo(state.pendingLogins)
            added = applied.added
            renamed = applied.renamed
            state.copy(cursor = toCursor, pendingLogins = applied.pendingLogins)
        }
        return if (written) AuditLogCheckResult.Checked(added, renamed) else AuditLogCheckResult.Discarded
    }

    /**
     * The only way this class writes state; init, cursor reset and [saveCheckResult] all use it.
     * Applies [change] only if the account is the same and the stored cursor is still [expectedCursor]; otherwise writes nothing
     * and returns false.
     */
    private suspend fun updateIfUnchanged(
        deviceId: String,
        expectedCursor: String?,
        change: (AuditLogCheckState) -> AuditLogCheckState,
    ): Boolean {
        var accepted = false
        stateStore.update { state ->
            if (!syncStore.isSignedIn() || syncStore.deviceId != deviceId || state.cursor != expectedCursor) return@update null
            accepted = true
            change(state).takeUnless { it == state }
        }
        return accepted
    }

    /** Maps an API error to the failure reason the check returns. */
    private fun AuditLogResult.toFailure(): AuditLogCheckResult.Failed = AuditLogCheckResult.Failed(
        when (this) {
            is AuditLogResult.RateLimited -> FailureReason.RATE_LIMITED
            is AuditLogResult.AuthError -> FailureReason.AUTH
            is AuditLogResult.NetworkError -> FailureReason.NETWORK
            is AuditLogResult.ServerError, is AuditLogResult.InvalidCursor, is AuditLogResult.Success -> FailureReason.SERVER
        },
    )

    /** The outcome of [fetchPagesAfter]. */
    private sealed interface FetchResult {
        data class Fetched(val newestFirst: List<AuditLogEntry>, val nextCursor: String) : FetchResult
        data object CursorRejected : FetchResult
        data class Failed(val failure: AuditLogCheckResult.Failed) : FetchResult
    }

    internal companion object {
        /**
         * The loop stops only when the server sends an empty page, so a server bug could otherwise loop forever.
         * The cap also keeps one check well under the 60 requests/min quota and bounds the RSA decrypts it does (one per entry).
         * Entries past the cap come on the next check, from the cursor stored here.
         */
        const val MAX_PAGES_PER_CHECK = 10

        /** Other reasons will not fix themselves, so the checker moves on rather than fetching the same pages on every sync. */
        val TEMPORARY_KEY_FAILURES = setOf(AccountInfoKeyUnavailableReason.KEYS_FETCH_FAILED, AccountInfoKeyUnavailableReason.RATE_LIMITED)
    }
}
