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

package com.duckduckgo.subscriptions.impl.auth

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.duckduckgo.subscriptions.impl.auth.BackupTokenState.EXPIRED
import com.duckduckgo.subscriptions.impl.auth.BackupTokenState.FLAG_OFF
import com.duckduckgo.subscriptions.impl.auth.BackupTokenState.NONE
import com.duckduckgo.subscriptions.impl.auth.BackupTokenState.OK
import com.duckduckgo.subscriptions.impl.auth.BackupTokenState.OTHER_ACCOUNT
import com.duckduckgo.subscriptions.impl.auth.BackupTokenState.STALE
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import com.duckduckgo.subscriptions.impl.repository.BackupRefreshToken
import com.duckduckgo.subscriptions.impl.repository.RefreshToken
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

interface BackupTokenManager {
    fun mintIfNeeded(
        spentRefreshToken: RefreshToken,
        accountExternalId: String,
    )

    suspend fun deleteIfAccountChanged(newAccountExternalId: String)

    /**
     * @return null outside the main process, which is the only one that reads the backup, and when the state cannot be read
     */
    suspend fun getState(accountExternalId: String?): BackupTokenState?
}

enum class BackupTokenState(val paramValue: String) {
    NONE("none"),
    OK("ok"),
    STALE("stale"),
    EXPIRED("expired"),
    OTHER_ACCOUNT("other_account"),
    FLAG_OFF("flag_off"),
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealBackupTokenManager @Inject constructor(
    private val authRepository: AuthRepository,
    private val authClient: AuthClient,
    private val authJwtValidator: AuthJwtValidator,
    private val subscriptionsFeature: Lazy<SubscriptionsFeature>,
    private val timeProvider: CurrentTimeProvider,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    @IsMainProcess private val isMainProcess: Boolean,
) : BackupTokenManager {

    private val mintMutex = Mutex()

    override fun mintIfNeeded(
        spentRefreshToken: RefreshToken,
        accountExternalId: String,
    ) {
        if (!isMainProcess) return
        coroutineScope.launch(dispatcherProvider.io()) {
            mintMutex.withLock {
                if (shouldMintBackup(accountExternalId)) {
                    logFailureAndReturn(operation = "mint", fallback = Unit) {
                        mint(spentRefreshToken = spentRefreshToken, accountExternalId = accountExternalId)
                    }
                }
            }
        }
    }

    private suspend fun shouldMintBackup(accountExternalId: String): Boolean =
        getState(accountExternalId) in STATES_THAT_NEED_A_NEW_BACKUP

    private suspend fun mint(
        spentRefreshToken: RefreshToken,
        accountExternalId: String,
    ) {
        val backup = requestBackup(spentRefreshToken)
        if (isForSignedInAccount(backup = backup, accountExternalId = accountExternalId)) {
            val saved = authRepository.saveBackupRefreshToken(backup)
            if (!saved) logcat(WARN) { "Subs: backup refresh token could not be saved" }
        }
    }

    private suspend fun requestBackup(spentRefreshToken: RefreshToken): BackupRefreshToken {
        val jwks = authClient.getJwks()
        val secondTokenPair = authClient.getTokens(spentRefreshToken.jwt)
        val refreshTokenClaims = authJwtValidator.validateRefreshToken(jwt = secondTokenPair.refreshToken, jwkSet = jwks)
        return BackupRefreshToken(
            jwt = secondTokenPair.refreshToken,
            expiresAt = refreshTokenClaims.expiresAt,
            accountExternalId = refreshTokenClaims.accountExternalId,
            issuedAt = getCurrentTime(),
        )
    }

    private suspend fun isForSignedInAccount(
        backup: BackupRefreshToken,
        accountExternalId: String,
    ): Boolean {
        val belongsToTheAccount = backup.accountExternalId == accountExternalId
        val accountIsStillSignedIn = authRepository.getAccount()?.externalId == accountExternalId
        return belongsToTheAccount && accountIsStillSignedIn
    }

    override suspend fun deleteIfAccountChanged(newAccountExternalId: String) {
        if (!isMainProcess) return
        logFailureAndReturn(operation = "account check", fallback = Unit) {
            if (hasBackupOfAnotherAccount(newAccountExternalId)) {
                authRepository.deleteBackupRefreshToken()
            }
        }
    }

    private suspend fun hasBackupOfAnotherAccount(accountExternalId: String): Boolean {
        val backup = authRepository.getBackupRefreshToken() ?: return false
        return backup.accountExternalId != accountExternalId
    }

    override suspend fun getState(accountExternalId: String?): BackupTokenState? {
        if (!isMainProcess) return null
        return logFailureAndReturn(operation = "state read", fallback = null) { readState(accountExternalId) }
    }

    private suspend fun readState(accountExternalId: String?): BackupTokenState {
        if (!isFlagEnabled()) return FLAG_OFF
        val backup = authRepository.getBackupRefreshToken() ?: return NONE
        val now = getCurrentTime()
        if (backup.accountExternalId != accountExternalId) return OTHER_ACCOUNT
        if (backup.expiresAt <= now) return EXPIRED
        if (backup.issuedAt + MAX_BACKUP_AGE <= now) return STALE
        return OK
    }

    // A failure in a backup step is logged and the fallback returned, so it cannot break the token refresh in progress
    private inline fun <T> logFailureAndReturn(
        operation: String,
        fallback: T,
        block: () -> T,
    ): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logcat(WARN) { "Subs: backup refresh token $operation failed with ${e.javaClass.simpleName}" }
        fallback
    }

    private suspend fun isFlagEnabled(): Boolean = withContext(dispatcherProvider.io()) {
        subscriptionsFeature.value.backupRefreshToken().isEnabled()
    }

    private fun getCurrentTime(): Instant = Instant.ofEpochMilli(timeProvider.currentTimeMillis())

    private companion object {
        val MAX_BACKUP_AGE: Duration = Duration.ofDays(14)
        val STATES_THAT_NEED_A_NEW_BACKUP = setOf(NONE, STALE, EXPIRED, OTHER_ACCOUNT)
    }
}
