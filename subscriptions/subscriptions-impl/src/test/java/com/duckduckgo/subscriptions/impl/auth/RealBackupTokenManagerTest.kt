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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.duckduckgo.subscriptions.impl.ValidatedTokenPair
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import com.duckduckgo.subscriptions.impl.repository.BackupRefreshToken
import com.duckduckgo.subscriptions.impl.repository.FakeSubscriptionsDataStore
import com.duckduckgo.subscriptions.impl.repository.RealAuthRepository
import com.duckduckgo.subscriptions.impl.repository.RefreshToken
import com.duckduckgo.subscriptions.impl.serp_promo.FakeSerpPromo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

class RealBackupTokenManagerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val authDataStore = FakeSubscriptionsDataStore()
    private val subscriptionsFeature: SubscriptionsFeature = FakeFeatureToggleFactory.create(SubscriptionsFeature::class.java)
    private val authRepository = RealAuthRepository(
        subscriptionsDataStore = authDataStore,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
        serpPromo = FakeSerpPromo(),
        subscriptionsFeature = lazy { subscriptionsFeature },
    )
    private val authClient: AuthClient = mock()
    private val authJwtValidator: AuthJwtValidator = mock()
    private val timeProvider = FakeTimeProvider()

    private val backupTokenManager = createManager(isMainProcess = true)

    @Before
    fun setUp() = runTest {
        givenFlagEnabled(true)
        authDataStore.externalId = ACCOUNT
        whenever(authClient.getJwks()).thenReturn(JWKS)
    }

    @Test
    fun whenNoBackupIsStoredThenStateIsNone() = runTest {
        assertEquals(BackupTokenState.NONE, backupTokenManager.getState(accountExternalId = ACCOUNT))
    }

    @Test
    fun whenBackupIsFreshAndForTheSameAccountThenStateIsOk() = runTest {
        givenStoredBackup()

        assertEquals(BackupTokenState.OK, backupTokenManager.getState(accountExternalId = ACCOUNT))
    }

    @Test
    fun whenBackupIsFourteenDaysOldThenStateIsStale() = runTest {
        givenStoredBackup(issuedAt = now() - Duration.ofDays(14))

        assertEquals(BackupTokenState.STALE, backupTokenManager.getState(accountExternalId = ACCOUNT))
    }

    @Test
    fun whenBackupIsJustUnderFourteenDaysOldThenStateIsOk() = runTest {
        givenStoredBackup(issuedAt = now() - Duration.ofDays(14) + Duration.ofSeconds(1))

        assertEquals(BackupTokenState.OK, backupTokenManager.getState(accountExternalId = ACCOUNT))
    }

    @Test
    fun whenBackupIsPastItsExpiryThenStateIsExpired() = runTest {
        givenStoredBackup(issuedAt = now() - Duration.ofDays(31), expiresAt = now() - Duration.ofDays(1))

        assertEquals(BackupTokenState.EXPIRED, backupTokenManager.getState(accountExternalId = ACCOUNT))
    }

    @Test
    fun whenBackupBelongsToAnotherAccountThenStateIsOtherAccount() = runTest {
        givenStoredBackup(accountExternalId = OTHER_ACCOUNT)

        assertEquals(BackupTokenState.OTHER_ACCOUNT, backupTokenManager.getState(accountExternalId = ACCOUNT))
    }

    @Test
    fun whenNoAccountIsSignedInThenStateOfAStoredBackupIsOtherAccount() = runTest {
        givenStoredBackup()

        assertEquals(BackupTokenState.OTHER_ACCOUNT, backupTokenManager.getState(accountExternalId = null))
    }

    @Test
    fun whenFlagIsOffThenStateIsFlagOffEvenWithAStoredBackup() = runTest {
        givenStoredBackup()
        givenFlagEnabled(false)

        assertEquals(BackupTokenState.FLAG_OFF, backupTokenManager.getState(accountExternalId = ACCOUNT))
    }

    @Test
    fun whenNotInMainProcessThenStateIsNotReported() = runTest {
        givenStoredBackup()

        assertNull(createManager(isMainProcess = false).getState(accountExternalId = ACCOUNT))
    }

    @Test
    fun whenNoBackupIsStoredThenMintStoresTheRefreshTokenOfTheSecondPair() = runTest {
        val mintedExpiry = now() + Duration.ofDays(30)
        givenBackendMints(refreshToken = MINTED_REFRESH_TOKEN, expiresAt = mintedExpiry)

        backupTokenManager.mintIfNeeded(spentRefreshToken = SPENT_REFRESH_TOKEN, accountExternalId = ACCOUNT)

        verify(authClient).getTokens(SPENT_REFRESH_TOKEN.jwt)
        assertEquals(
            BackupRefreshToken(
                jwt = MINTED_REFRESH_TOKEN,
                expiresAt = mintedExpiry,
                accountExternalId = ACCOUNT,
                issuedAt = now(),
            ),
            authRepository.getBackupRefreshToken(),
        )
    }

    @Test
    fun whenStoredBackupIsStaleExpiredOrFromAnotherAccountThenMintReplacesIt() = runTest {
        givenBackendMints(refreshToken = MINTED_REFRESH_TOKEN)
        val backupsThatNeedReplacing: List<suspend () -> Unit> = listOf(
            { givenStoredBackup(issuedAt = now() - Duration.ofDays(14)) },
            { givenStoredBackup(issuedAt = now() - Duration.ofDays(31), expiresAt = now() - Duration.ofDays(1)) },
            { givenStoredBackup(accountExternalId = OTHER_ACCOUNT) },
        )

        backupsThatNeedReplacing.forEach { givenBackup ->
            givenBackup()

            backupTokenManager.mintIfNeeded(spentRefreshToken = SPENT_REFRESH_TOKEN, accountExternalId = ACCOUNT)

            assertEquals(MINTED_REFRESH_TOKEN, authRepository.getBackupRefreshToken()?.jwt)
            assertEquals(ACCOUNT, authRepository.getBackupRefreshToken()?.accountExternalId)
        }
    }

    @Test
    fun whenStoredBackupIsUsableThenMintDoesNotCallTheBackend() = runTest {
        givenStoredBackup(jwt = STORED_BACKUP)

        backupTokenManager.mintIfNeeded(spentRefreshToken = SPENT_REFRESH_TOKEN, accountExternalId = ACCOUNT)

        verify(authClient, never()).getTokens(any())
        assertEquals(STORED_BACKUP, authRepository.getBackupRefreshToken()?.jwt)
    }

    @Test
    fun whenFlagIsOffThenMintDoesNothing() = runTest {
        givenFlagEnabled(false)

        backupTokenManager.mintIfNeeded(spentRefreshToken = SPENT_REFRESH_TOKEN, accountExternalId = ACCOUNT)

        verify(authClient, never()).getTokens(any())
        assertNull(authRepository.getBackupRefreshToken())
    }

    @Test
    fun whenNotInMainProcessThenMintDoesNothing() = runTest {
        createManager(isMainProcess = false).mintIfNeeded(spentRefreshToken = SPENT_REFRESH_TOKEN, accountExternalId = ACCOUNT)

        verify(authClient, never()).getTokens(any())
        assertNull(authRepository.getBackupRefreshToken())
    }

    @Test
    fun whenMintedTokenBelongsToAnotherAccountThenItIsNotStored() = runTest {
        givenBackendMints(refreshToken = MINTED_REFRESH_TOKEN, accountExternalId = OTHER_ACCOUNT)

        backupTokenManager.mintIfNeeded(spentRefreshToken = SPENT_REFRESH_TOKEN, accountExternalId = ACCOUNT)

        assertNull(authRepository.getBackupRefreshToken())
    }

    @Test
    fun whenAccountChangesWhileMintingThenTheMintedTokenIsNotStored() = runTest {
        givenBackendMints(refreshToken = MINTED_REFRESH_TOKEN)
        whenever(authClient.getTokens(any())).doSuspendableAnswer {
            authDataStore.externalId = OTHER_ACCOUNT
            TokenPair(
                accessToken = "minted access token",
                refreshToken = MINTED_REFRESH_TOKEN,
            )
        }

        backupTokenManager.mintIfNeeded(spentRefreshToken = SPENT_REFRESH_TOKEN, accountExternalId = ACCOUNT)

        assertNull(authRepository.getBackupRefreshToken())
    }

    @Test
    fun whenUserSignsOutWhileMintingThenTheMintedTokenIsNotStored() = runTest {
        givenBackendMints(refreshToken = MINTED_REFRESH_TOKEN)
        whenever(authClient.getTokens(any())).doSuspendableAnswer {
            authDataStore.clearTokens()
            TokenPair(
                accessToken = "minted access token",
                refreshToken = MINTED_REFRESH_TOKEN,
            )
        }

        backupTokenManager.mintIfNeeded(spentRefreshToken = SPENT_REFRESH_TOKEN, accountExternalId = ACCOUNT)

        assertNull(authRepository.getBackupRefreshToken())
    }

    @Test
    fun whenMintedTokenFailsValidationThenItIsNotStored() = runTest {
        givenBackendMints(refreshToken = MINTED_REFRESH_TOKEN)
        val invalidSignature = TokenValidationException(reason = TokenValidationFailureReason.InvalidSignature)
        whenever(authJwtValidator.validateRefreshToken(jwt = MINTED_REFRESH_TOKEN, jwkSet = JWKS)).thenThrow(invalidSignature)

        backupTokenManager.mintIfNeeded(spentRefreshToken = SPENT_REFRESH_TOKEN, accountExternalId = ACCOUNT)

        assertNull(authRepository.getBackupRefreshToken())
    }

    @Test
    fun whenTwoMintsAreRequestedAtOnceThenTheBackendIsCalledOnce() = runTest {
        val backendResponse = CompletableDeferred<TokenPair>()
        givenBackendMints(refreshToken = MINTED_REFRESH_TOKEN)
        whenever(authClient.getTokens(any())).doSuspendableAnswer { backendResponse.await() }

        backupTokenManager.mintIfNeeded(spentRefreshToken = SPENT_REFRESH_TOKEN, accountExternalId = ACCOUNT)
        backupTokenManager.mintIfNeeded(spentRefreshToken = SPENT_REFRESH_TOKEN, accountExternalId = ACCOUNT)
        backendResponse.complete(
            TokenPair(
                accessToken = "minted access token",
                refreshToken = MINTED_REFRESH_TOKEN,
            ),
        )

        verify(authClient, times(1)).getTokens(any())
        assertEquals(MINTED_REFRESH_TOKEN, authRepository.getBackupRefreshToken()?.jwt)
    }

    @Test
    fun whenMintFailsThenTheStoredBackupIsKept() = runTest {
        givenStoredBackup(jwt = STORED_BACKUP, issuedAt = now() - Duration.ofDays(14))
        whenever(authClient.getTokens(any())).doSuspendableAnswer { throw IOException() }

        backupTokenManager.mintIfNeeded(spentRefreshToken = SPENT_REFRESH_TOKEN, accountExternalId = ACCOUNT)

        assertEquals(STORED_BACKUP, authRepository.getBackupRefreshToken()?.jwt)
    }

    @Test
    fun whenBackendAcceptsTheBackupThenRecoveryReturnsTheNewTokensAndTheSpentBackup() = runTest {
        val backupExpiry = now() + Duration.ofDays(20)
        givenStoredBackup(jwt = STORED_BACKUP, expiresAt = backupExpiry)
        val recoveredTokens = givenBackendAccepts(refreshToken = STORED_BACKUP)

        val result = backupTokenManager.recover(accountExternalId = ACCOUNT)

        val expectedSpentBackup = RefreshToken(
            jwt = STORED_BACKUP,
            expiresAt = backupExpiry,
        )
        val expected = BackupRecoveryResult.Recovered(
            tokens = recoveredTokens,
            spentBackupRefreshToken = expectedSpentBackup,
        )
        assertEquals(expected, result)
    }

    @Test
    fun whenBackupIsDueForRenewalButNotExpiredThenItStillRecovers() = runTest {
        givenStoredBackup(jwt = STORED_BACKUP, issuedAt = now() - Duration.ofDays(20))
        givenBackendAccepts(refreshToken = STORED_BACKUP)

        val result = backupTokenManager.recover(accountExternalId = ACCOUNT)

        assertTrue(result is BackupRecoveryResult.Recovered)
    }

    @Test
    fun whenNoBackupIsStoredThenRecoveryIsNotAvailableAndTheBackendIsNotCalled() = runTest {
        val result = backupTokenManager.recover(accountExternalId = ACCOUNT)

        assertEquals(BackupRecoveryResult.Failure.NotAvailable, result)
        verify(authClient, never()).getTokens(any())
    }

    @Test
    fun whenBackupBelongsToAnotherAccountThenItIsDeletedAndNotSent() = runTest {
        givenStoredBackup(accountExternalId = OTHER_ACCOUNT)

        val result = backupTokenManager.recover(accountExternalId = ACCOUNT)

        assertEquals(BackupRecoveryResult.Failure.NotAvailable, result)
        assertNull(authRepository.getBackupRefreshToken())
        verify(authClient, never()).getTokens(any())
    }

    @Test
    fun whenBackupHasExpiredThenItIsDeletedAndNotSent() = runTest {
        givenStoredBackup(issuedAt = now() - Duration.ofDays(31), expiresAt = now() - Duration.ofDays(1))

        val result = backupTokenManager.recover(accountExternalId = ACCOUNT)

        assertEquals(BackupRecoveryResult.Failure.NotAvailable, result)
        assertNull(authRepository.getBackupRefreshToken())
        verify(authClient, never()).getTokens(any())
    }

    @Test
    fun whenBackendRejectsTheBackupThenItIsDeleted() = runTest {
        givenStoredBackup()
        whenever(authClient.getTokens(any())).doSuspendableAnswer { throw httpException(code = 400) }

        val result = backupTokenManager.recover(accountExternalId = ACCOUNT)

        assertEquals(BackupRecoveryResult.Failure.Rejected, result)
        assertNull(authRepository.getBackupRefreshToken())
    }

    @Test
    fun whenTheNetworkFailsDuringRecoveryThenTheBackupIsKept() = runTest {
        givenStoredBackup(jwt = STORED_BACKUP)
        whenever(authClient.getTokens(any())).doSuspendableAnswer { throw IOException() }

        val result = backupTokenManager.recover(accountExternalId = ACCOUNT)

        assertEquals(BackupRecoveryResult.Failure.NetworkError, result)
        assertEquals(STORED_BACKUP, authRepository.getBackupRefreshToken()?.jwt)
    }

    @Test
    fun whenTheBackendHasAnOutageDuringRecoveryThenTheBackupIsKept() = runTest {
        givenStoredBackup(jwt = STORED_BACKUP)
        whenever(authClient.getTokens(any())).doSuspendableAnswer { throw httpException(code = 503) }

        val result = backupTokenManager.recover(accountExternalId = ACCOUNT)

        assertEquals(BackupRecoveryResult.Failure.NetworkError, result)
        assertEquals(STORED_BACKUP, authRepository.getBackupRefreshToken()?.jwt)
    }

    @Test
    fun whenRecoveredTokensBelongToAnotherAccountThenTheBackupIsDeleted() = runTest {
        givenStoredBackup(jwt = STORED_BACKUP)
        givenBackendAccepts(refreshToken = STORED_BACKUP, accountExternalId = OTHER_ACCOUNT)

        val result = backupTokenManager.recover(accountExternalId = ACCOUNT)

        assertEquals(BackupRecoveryResult.Failure.Rejected, result)
        assertNull(authRepository.getBackupRefreshToken())
    }

    @Test
    fun whenRecoveredTokensFailValidationThenTheSpentBackupIsDeleted() = runTest {
        givenStoredBackup(jwt = STORED_BACKUP)
        givenBackendAccepts(refreshToken = STORED_BACKUP)
        val invalidSignature = TokenValidationException(reason = TokenValidationFailureReason.InvalidSignature)
        whenever(authJwtValidator.validateAccessToken(jwt = RECOVERED_ACCESS_TOKEN, jwkSet = JWKS)).thenThrow(invalidSignature)

        val result = backupTokenManager.recover(accountExternalId = ACCOUNT)

        assertEquals(BackupRecoveryResult.Failure.Rejected, result)
        assertNull(authRepository.getBackupRefreshToken())
    }

    @Test
    fun whenBackendRefusesTheBackupWithAnotherClientErrorThenItIsDeleted() = runTest {
        givenStoredBackup()
        whenever(authClient.getTokens(any())).doSuspendableAnswer { throw httpException(code = 401) }

        val result = backupTokenManager.recover(accountExternalId = ACCOUNT)

        assertEquals(BackupRecoveryResult.Failure.Rejected, result)
        assertNull(authRepository.getBackupRefreshToken())
    }

    @Test
    fun whenFlagIsOffThenRecoveryIsNotAvailableAndTheStoredBackupIsKept() = runTest {
        givenStoredBackup(jwt = STORED_BACKUP)
        givenFlagEnabled(false)

        val result = backupTokenManager.recover(accountExternalId = ACCOUNT)

        assertEquals(BackupRecoveryResult.Failure.NotAvailable, result)
        assertEquals(STORED_BACKUP, authRepository.getBackupRefreshToken()?.jwt)
        verify(authClient, never()).getTokens(any())
    }

    @Test
    fun whenNotInMainProcessThenRecoveryIsNotAvailableAndTheStoredBackupIsKept() = runTest {
        givenStoredBackup(jwt = STORED_BACKUP)

        val result = createManager(isMainProcess = false).recover(accountExternalId = ACCOUNT)

        assertEquals(BackupRecoveryResult.Failure.NotAvailable, result)
        assertEquals(STORED_BACKUP, authRepository.getBackupRefreshToken()?.jwt)
        verify(authClient, never()).getTokens(any())
    }

    @Test
    fun whenNewTokensBelongToAnotherAccountThenBackupIsDeleted() = runTest {
        givenStoredBackup(accountExternalId = ACCOUNT)

        backupTokenManager.deleteIfAccountChanged(newAccountExternalId = OTHER_ACCOUNT)

        assertNull(authRepository.getBackupRefreshToken())
    }

    @Test
    fun whenNewTokensBelongToTheSameAccountThenBackupIsKept() = runTest {
        givenStoredBackup(jwt = STORED_BACKUP, accountExternalId = ACCOUNT)

        backupTokenManager.deleteIfAccountChanged(newAccountExternalId = ACCOUNT)

        assertEquals(STORED_BACKUP, authRepository.getBackupRefreshToken()?.jwt)
    }

    @Test
    fun whenNotInMainProcessThenBackupOfAnotherAccountIsKept() = runTest {
        givenStoredBackup(jwt = STORED_BACKUP, accountExternalId = ACCOUNT)

        createManager(isMainProcess = false).deleteIfAccountChanged(newAccountExternalId = OTHER_ACCOUNT)

        assertEquals(STORED_BACKUP, authRepository.getBackupRefreshToken()?.jwt)
    }

    @Test
    fun whenFlagIsOffThenBackupOfAnotherAccountIsStillDeleted() = runTest {
        givenStoredBackup(accountExternalId = ACCOUNT)
        givenFlagEnabled(false)

        backupTokenManager.deleteIfAccountChanged(newAccountExternalId = OTHER_ACCOUNT)

        assertNull(authRepository.getBackupRefreshToken())
    }

    @Test
    fun whenTheStoreFailsThenStateIsNotReportedAndNothingIsThrown() = runTest {
        val manager = createManager(isMainProcess = true, authRepository = failingAuthRepository())

        assertNull(manager.getState(accountExternalId = ACCOUNT))
    }

    @Test
    fun whenTheStoreFailsThenTheAccountCheckDoesNotThrow() = runTest {
        val manager = createManager(isMainProcess = true, authRepository = failingAuthRepository())

        manager.deleteIfAccountChanged(newAccountExternalId = OTHER_ACCOUNT)
    }

    private suspend fun failingAuthRepository(): AuthRepository = mock<AuthRepository>().also {
        whenever(it.getBackupRefreshToken()).doSuspendableAnswer { throw IOException() }
    }

    private fun createManager(
        isMainProcess: Boolean,
        authRepository: AuthRepository = this.authRepository,
    ) = RealBackupTokenManager(
        authRepository = authRepository,
        authClient = authClient,
        authJwtValidator = authJwtValidator,
        subscriptionsFeature = lazy { subscriptionsFeature },
        timeProvider = timeProvider,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
        coroutineScope = coroutineRule.testScope,
        isMainProcess = isMainProcess,
    )

    private fun givenFlagEnabled(enabled: Boolean) {
        subscriptionsFeature.backupRefreshToken().setRawStoredState(State(remoteEnableState = enabled))
    }

    private suspend fun givenStoredBackup(
        jwt: String = STORED_BACKUP,
        accountExternalId: String = ACCOUNT,
        issuedAt: Instant = now(),
        expiresAt: Instant = issuedAt + Duration.ofDays(30),
    ) {
        authRepository.saveBackupRefreshToken(
            BackupRefreshToken(
                jwt = jwt,
                expiresAt = expiresAt,
                accountExternalId = accountExternalId,
                issuedAt = issuedAt,
            ),
        )
    }

    private suspend fun givenBackendMints(
        refreshToken: String,
        accountExternalId: String = ACCOUNT,
        expiresAt: Instant = now() + Duration.ofDays(30),
    ) {
        val secondTokenPair = TokenPair(
            accessToken = "minted access token",
            refreshToken = refreshToken,
        )
        val refreshTokenClaims = RefreshTokenClaims(
            expiresAt = expiresAt,
            accountExternalId = accountExternalId,
        )
        whenever(authClient.getTokens(any())).thenReturn(secondTokenPair)
        whenever(authJwtValidator.validateRefreshToken(jwt = refreshToken, jwkSet = JWKS)).thenReturn(refreshTokenClaims)
    }

    private suspend fun givenBackendAccepts(
        refreshToken: String,
        accountExternalId: String = ACCOUNT,
    ): ValidatedTokenPair {
        val accessTokenClaims = AccessTokenClaims(
            expiresAt = now() + Duration.ofHours(4),
            accountExternalId = accountExternalId,
            email = null,
            entitlements = emptyList(),
        )
        val refreshTokenClaims = RefreshTokenClaims(
            expiresAt = now() + Duration.ofDays(30),
            accountExternalId = accountExternalId,
        )
        val recoveredTokenPair = TokenPair(
            accessToken = RECOVERED_ACCESS_TOKEN,
            refreshToken = RECOVERED_REFRESH_TOKEN,
        )
        whenever(authClient.getTokens(refreshToken)).thenReturn(recoveredTokenPair)
        whenever(authJwtValidator.validateAccessToken(jwt = RECOVERED_ACCESS_TOKEN, jwkSet = JWKS)).thenReturn(accessTokenClaims)
        whenever(authJwtValidator.validateRefreshToken(jwt = RECOVERED_REFRESH_TOKEN, jwkSet = JWKS)).thenReturn(refreshTokenClaims)
        return ValidatedTokenPair(
            accessToken = RECOVERED_ACCESS_TOKEN,
            accessTokenClaims = accessTokenClaims,
            refreshToken = RECOVERED_REFRESH_TOKEN,
            refreshTokenClaims = refreshTokenClaims,
        )
    }

    private fun httpException(code: Int): HttpException {
        val errorBody = "".toResponseBody(contentType = null)
        return HttpException(Response.error<Void>(code, errorBody))
    }

    private fun now(): Instant = timeProvider.currentTime

    private class FakeTimeProvider : CurrentTimeProvider {
        val currentTime: Instant = Instant.parse("2026-11-16T00:00:00Z")

        override fun elapsedRealtime(): Long = throw UnsupportedOperationException()
        override fun currentTimeMillis(): Long = currentTime.toEpochMilli()
        override fun localDateTimeNow(): LocalDateTime = throw UnsupportedOperationException()
    }

    private companion object {
        const val ACCOUNT = "1234"
        const val OTHER_ACCOUNT = "5678"
        const val JWKS = "fake jwks"
        const val STORED_BACKUP = "stored backup refresh token"
        const val MINTED_REFRESH_TOKEN = "minted refresh token"
        const val RECOVERED_ACCESS_TOKEN = "recovered access token"
        const val RECOVERED_REFRESH_TOKEN = "recovered refresh token"
        val SPENT_REFRESH_TOKEN = RefreshToken(
            jwt = "spent refresh token",
            expiresAt = Instant.parse("2026-12-01T00:00:00Z"),
        )
    }
}
