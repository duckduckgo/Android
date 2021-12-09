/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.email

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.email.AppEmailManager.WaitlistState.*
import com.duckduckgo.app.email.AppEmailManager.Companion.DUCK_EMAIL_DOMAIN
import com.duckduckgo.app.email.AppEmailManager.Companion.UNKNOWN_COHORT
import com.duckduckgo.app.email.api.EmailAlias
import com.duckduckgo.app.email.api.EmailInviteCodeResponse
import com.duckduckgo.app.email.api.EmailService
import com.duckduckgo.app.email.api.WaitlistResponse
import com.duckduckgo.app.email.api.WaitlistStatusResponse
import com.duckduckgo.app.email.db.EmailDataStore
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@FlowPreview
@ExperimentalCoroutinesApi
class AppEmailManagerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val mockEmailService: EmailService = mock()
    private val mockEmailDataStore: EmailDataStore = mock()
    private val aliasSharedFlow = MutableStateFlow<String?>(null)
    lateinit var testee: AppEmailManager

    @Before
    fun setup() {
        whenever(mockEmailDataStore.nextAliasFlow()).thenReturn(aliasSharedFlow.asStateFlow())
        testee = AppEmailManager(mockEmailService, mockEmailDataStore, coroutineRule.testDispatcherProvider, TestCoroutineScope())
    }

    @Test
    fun whenFetchAliasFromServiceThenStoreAliasAddingDuckDomain() = coroutineRule.runBlocking {
        whenever(mockEmailDataStore.emailToken).thenReturn("token")
        whenever(mockEmailService.newAlias(any())).thenReturn(EmailAlias("test"))
        testee.getAlias()

        verify(mockEmailDataStore).nextAlias = "test$DUCK_EMAIL_DOMAIN"
    }

    @Test
    fun whenFetchAliasFromServiceAndTokenDoesNotExistThenDoNothing() = coroutineRule.runBlocking {
        whenever(mockEmailDataStore.emailToken).thenReturn(null)
        testee.getAlias()

        verify(mockEmailService, never()).newAlias(any())
    }

    @Test
    fun whenFetchAliasFromServiceAndAddressIsBlankThenStoreNullTwice() = coroutineRule.runBlocking {
        whenever(mockEmailDataStore.emailToken).thenReturn("token")
        whenever(mockEmailService.newAlias(any())).thenReturn(EmailAlias(""))
        testee.getAlias()

        verify(mockEmailDataStore, times(2)).nextAlias = null
    }

    @Test
    fun whenGetAliasThenReturnNextAlias() = coroutineRule.runBlocking {
        givenNextAliasExists()

        assertEquals("alias", testee.getAlias())
    }

    @Test
    fun whenGetAliasIfNextAliasDoesNotExistThenReturnNull() {
        assertNull(testee.getAlias())
    }

    @Test
    fun whenGetAliasThenClearNextAlias() {
        testee.getAlias()

        verify(mockEmailDataStore).nextAlias = null
    }

    @Test
    fun whenIsSignedInAndTokenDoesNotExistThenReturnFalse() {
        whenever(mockEmailDataStore.emailUsername).thenReturn("username")
        whenever(mockEmailDataStore.nextAlias).thenReturn("alias")

        assertFalse(testee.isSignedIn())
    }

    @Test
    fun whenIsSignedInAndUsernameDoesNotExistThenReturnFalse() {
        whenever(mockEmailDataStore.emailToken).thenReturn("token")
        whenever(mockEmailDataStore.nextAlias).thenReturn("alias")

        assertFalse(testee.isSignedIn())
    }

    @Test
    fun whenIsSignedInAndTokenAndUsernameExistThenReturnTrue() {
        whenever(mockEmailDataStore.emailToken).thenReturn("token")
        whenever(mockEmailDataStore.emailUsername).thenReturn("username")

        assertTrue(testee.isSignedIn())
    }

    @Test
    fun whenStoreCredentialsThenGenerateNewAlias() = coroutineRule.runBlocking {
        whenever(mockEmailDataStore.emailToken).thenReturn("token")
        whenever(mockEmailService.newAlias(any())).thenReturn(EmailAlias(""))

        testee.storeCredentials("token", "username", "cohort")

        verify(mockEmailService).newAlias(any())
    }

    @Test
    fun whenStoreCredentialsThenCredentialsAreStoredInDataStore() {
        testee.storeCredentials("token", "username", "cohort")

        verify(mockEmailDataStore).emailUsername = "username"
        verify(mockEmailDataStore).emailToken = "token"
        verify(mockEmailDataStore).cohort = "cohort"
    }

    @Test
    fun whenStoreCredentialsIfCredentialsWereCorrectlyStoredThenIsSignedInChannelSendsTrue() = coroutineRule.runBlocking {
        whenever(mockEmailDataStore.emailToken).thenReturn("token")
        whenever(mockEmailDataStore.emailUsername).thenReturn("username")

        testee.storeCredentials("token", "username", "cohort")

        assertTrue(testee.signedInFlow().first())
    }

    @Test
    fun whenStoreCredentialsIfCredentialsWereNotCorrectlyStoredThenIsSignedInChannelSendsFalse() = coroutineRule.runBlocking {
        testee.storeCredentials("token", "username", "cohort")

        assertFalse(testee.signedInFlow().first())
    }

    @Test
    fun whenSignedOutThenClearEmailDataAndAliasIsNull() {
        testee.signOut()

        verify(mockEmailDataStore).emailUsername = null
        verify(mockEmailDataStore).emailToken = null
        verify(mockEmailDataStore).nextAlias = null
        assertNull(testee.getAlias())
    }

    @Test
    fun whenSignedOutThenIsSignedInChannelSendsFalse() = coroutineRule.runBlocking {
        testee.signOut()

        assertFalse(testee.signedInFlow().first())
    }

    @Test
    fun whenGetEmailAddressThenDuckEmailDomainIsAppended() {
        whenever(mockEmailDataStore.emailUsername).thenReturn("username")

        assertEquals("username$DUCK_EMAIL_DOMAIN", testee.getEmailAddress())
    }

    @Test
    fun whenWaitlistStateIfTimestampExistsCodeDoesNotExistAndSendNotificationIsTrueThenReturnJoinedQueueWithTrue() {
        whenever(mockEmailDataStore.waitlistTimestamp).thenReturn(1234)
        whenever(mockEmailDataStore.sendNotification).thenReturn(true)

        assertEquals(JoinedQueue(true), testee.waitlistState())
    }

    @Test
    fun whenWaitlistStateIfTimestampExistsCodeDoesNotExistAndSendNotificationIsFalseThenReturnJoinedQueueWithFalse() {
        whenever(mockEmailDataStore.waitlistTimestamp).thenReturn(1234)
        whenever(mockEmailDataStore.sendNotification).thenReturn(false)

        assertEquals(JoinedQueue(false), testee.waitlistState())
    }

    @Test
    fun whenWaitlistStateIfTimestampExistsAndCodeExistsThenReturnInBeta() {
        whenever(mockEmailDataStore.waitlistTimestamp).thenReturn(1234)
        whenever(mockEmailDataStore.inviteCode).thenReturn("abcde")

        assertEquals(InBeta, testee.waitlistState())
    }

    @Test
    fun whenWaitlistStateIfTimestampAndCodeDoesNotExistThenReturnNotJoinedQueue() {
        whenever(mockEmailDataStore.waitlistTimestamp).thenReturn(-1)
        whenever(mockEmailDataStore.inviteCode).thenReturn(null)

        assertEquals(NotJoinedQueue, testee.waitlistState())
    }

    @Test
    fun whenJoinWaitlistIfTimestampAndTokenDidNotExistThenStoreTimestampAndToken() {
        whenever(mockEmailDataStore.waitlistTimestamp).thenReturn(-1)
        whenever(mockEmailDataStore.waitlistToken).thenReturn(null)

        testee.joinWaitlist(1234, "abcde")

        verify(mockEmailDataStore).waitlistTimestamp = 1234
        verify(mockEmailDataStore).waitlistToken = "abcde"
    }
    @Test
    fun whenJoinWaitlistIfTimestampAndTokenDidExistThenStoreTimestampAndTokenAreNotStored() {
        whenever(mockEmailDataStore.waitlistTimestamp).thenReturn(1234)
        whenever(mockEmailDataStore.waitlistToken).thenReturn("abcde")

        testee.joinWaitlist(4321, "edcba")

        verify(mockEmailDataStore, never()).waitlistTimestamp = 4321
        verify(mockEmailDataStore, never()).waitlistToken = "edcba"
    }

    @Test
    fun whenGetInviteCodeIfCodeExistsThenReturnCode() {
        whenever(mockEmailDataStore.inviteCode).thenReturn("abcde")
        assertEquals("abcde", testee.getInviteCode())
    }

    @Test
    fun whenGetInviteCodeIfCodeDoesNotExistThenReturnEmpty() {
        whenever(mockEmailDataStore.inviteCode).thenReturn(null)
        assertEquals("", testee.getInviteCode())
    }

    @Test
    fun whenDoesCodeAlreadyExistIfCodeExistsThenReturnTrue() {
        whenever(mockEmailDataStore.inviteCode).thenReturn("inviteCode")

        assertTrue(testee.doesCodeAlreadyExist())
    }

    @Test
    fun whenDoesCodeAlreadyExistIfCodeIsNullThenReturnFalse() {
        whenever(mockEmailDataStore.inviteCode).thenReturn(null)

        assertFalse(testee.doesCodeAlreadyExist())
    }

    @Test
    fun whenFetchInviteCodeIfCodeAlreadyExistsThenReturnCodeExisted() = coroutineRule.runBlocking {
        whenever(mockEmailDataStore.inviteCode).thenReturn("inviteCode")

        assertEquals(AppEmailManager.FetchCodeResult.CodeExisted, testee.fetchInviteCode())
    }

    @Test
    fun whenFetchInviteCodeIfTimestampIsSmallerThanQueueTimestampThenCallGetCode() = coroutineRule.runBlocking {
        givenUserIsInWaitlist()
        whenever(mockEmailService.waitlistStatus()).thenReturn(WaitlistStatusResponse(12345))

        testee.fetchInviteCode()

        verify(mockEmailService).getCode("token")
    }

    @Test
    fun whenFetchInviteCodeIfTimestampIsEqualsThanQueueTimestampThenCallGetCode() = coroutineRule.runBlocking {
        givenUserIsInWaitlist()
        whenever(mockEmailService.waitlistStatus()).thenReturn(WaitlistStatusResponse(1234))

        testee.fetchInviteCode()

        verify(mockEmailService).getCode("token")
    }

    @Test
    fun whenFetchInviteCodeIfUserIsTopOfQueueAndCodeAvailableThenReturnCode() = coroutineRule.runBlocking {
        givenUserIsTopOfTheQueue()
        whenever(mockEmailService.getCode(any())).thenReturn(EmailInviteCodeResponse("code"))

        assertEquals(AppEmailManager.FetchCodeResult.Code, testee.fetchInviteCode())
    }

    @Test
    fun whenFetchInviteCodeIfUserIsTopOfQueueAndCodeNotAvailableThenReturnNoCode() = coroutineRule.runBlocking {
        givenUserIsTopOfTheQueue()
        whenever(mockEmailService.getCode(any())).thenReturn(EmailInviteCodeResponse(""))

        assertEquals(AppEmailManager.FetchCodeResult.NoCode, testee.fetchInviteCode())
    }

    @Test
    fun whenFetchInviteCodeIfUserIsTopOfQueueAndCodeServiceNotAvailableThenReturnNoCode() = coroutineRule.runBlocking {
        testee = AppEmailManager(TestEmailService(), mockEmailDataStore, coroutineRule.testDispatcherProvider, TestCoroutineScope())
        givenUserIsTopOfTheQueue()

        assertEquals(AppEmailManager.FetchCodeResult.NoCode, testee.fetchInviteCode())
    }

    @Test
    fun whenFetchInviteCodeIfUserInTheQueueAndStatusServiceNotAvailableThenReturnNoCode() = coroutineRule.runBlocking {
        testee = AppEmailManager(TestEmailService(), mockEmailDataStore, coroutineRule.testDispatcherProvider, TestCoroutineScope())
        givenUserIsInWaitlist()

        assertEquals(AppEmailManager.FetchCodeResult.NoCode, testee.fetchInviteCode())
    }

    @Test
    fun whenNotifyOnJoinedWaitlistThenSendNotificationSetToTrue() {
        testee.notifyOnJoinedWaitlist()
        verify(mockEmailDataStore).sendNotification = true
    }

    @Test
    fun whenGetCohortThenReturnCohort() {
        whenever(mockEmailDataStore.cohort).thenReturn("cohort")

        assertEquals("cohort", testee.getCohort())
    }

    @Test
    fun whenGetCohortIfCohortIsNullThenReturnUnknown() {
        whenever(mockEmailDataStore.cohort).thenReturn(null)

        assertEquals(UNKNOWN_COHORT, testee.getCohort())
    }

    @Test
    fun whenGetCohortIfCohortIsEmtpyThenReturnUnknown() {
        whenever(mockEmailDataStore.cohort).thenReturn("")

        assertEquals(UNKNOWN_COHORT, testee.getCohort())
    }

    @Test
    fun whenIsEmailFeatureSupportedAndEncryptionCanBeUsedThenReturnTrue() {
        whenever(mockEmailDataStore.canUseEncryption()).thenReturn(true)

        assertTrue(testee.isEmailFeatureSupported())
    }

    @Test
    fun whenGetLastUsedDateIfNullThenReturnEmpty() {
        assertEquals("", testee.getLastUsedDate())
    }

    @Test
    fun whenGetLastUsedDateIfNotNullThenReturnValueFromStore() {
        whenever(mockEmailDataStore.lastUsedDate).thenReturn("2021-01-01")
        assertEquals("2021-01-01", testee.getLastUsedDate())
    }

    @Test
    fun whenIsEmailFeatureSupportedAndEncryptionCannotBeUsedThenReturnFalse() {
        whenever(mockEmailDataStore.canUseEncryption()).thenReturn(false)

        assertFalse(testee.isEmailFeatureSupported())
    }

    private fun givenUserIsInWaitlist() {
        whenever(mockEmailDataStore.waitlistTimestamp).thenReturn(1234)
        whenever(mockEmailDataStore.waitlistToken).thenReturn("token")
    }

    private fun givenUserIsTopOfTheQueue() = coroutineRule.runBlocking {
        givenUserIsInWaitlist()
        whenever(mockEmailService.waitlistStatus()).thenReturn(WaitlistStatusResponse(1234))
    }

    private suspend fun givenNextAliasExists() {
        aliasSharedFlow.emit("alias")
    }

    class TestEmailService : EmailService {
        override suspend fun newAlias(authorization: String): EmailAlias = EmailAlias("alias")
        override suspend fun joinWaitlist(): WaitlistResponse = WaitlistResponse("token", 12345)
        override suspend fun waitlistStatus(): WaitlistStatusResponse {
            throw Exception()
        }
        override suspend fun getCode(token: String): EmailInviteCodeResponse {
            throw Exception()
        }
    }
}
