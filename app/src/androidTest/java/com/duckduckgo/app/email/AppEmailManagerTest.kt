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
import com.duckduckgo.app.email.AppEmailManager.Companion.DUCK_EMAIL_DOMAIN
import com.duckduckgo.app.email.AppEmailManager.Companion.UNKNOWN_COHORT
import com.duckduckgo.app.email.AppEmailManager.WaitlistState.InBeta
import com.duckduckgo.app.email.AppEmailManager.WaitlistState.JoinedQueue
import com.duckduckgo.app.email.AppEmailManager.WaitlistState.NotJoinedQueue
import com.duckduckgo.app.email.api.EmailAlias
import com.duckduckgo.app.email.api.EmailInviteCodeResponse
import com.duckduckgo.app.email.api.EmailService
import com.duckduckgo.app.email.api.WaitlistResponse
import com.duckduckgo.app.email.api.WaitlistStatusResponse
import com.duckduckgo.app.email.db.EmailDataStore
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private val mockEmailDataStore: EmailDataStore = FakeEmailDataStore()
    lateinit var testee: AppEmailManager

    @Before
    fun setup() {
        testee = AppEmailManager(mockEmailService, mockEmailDataStore, coroutineRule.testDispatcherProvider, TestScope())
    }

    @Test
    fun whenFetchAliasFromServiceThenStoreAliasAddingDuckDomain() = runTest {
        mockEmailDataStore.emailToken = "token"
        whenever(mockEmailService.newAlias(any())).thenReturn(EmailAlias("test"))
        testee.getAlias()

        assertEquals("test$DUCK_EMAIL_DOMAIN", mockEmailDataStore.nextAlias)
    }

    @Test
    fun whenFetchAliasFromServiceAndTokenDoesNotExistThenDoNothing() = runTest {
        mockEmailDataStore.emailToken = null
        testee.getAlias()

        verify(mockEmailService, never()).newAlias(any())
    }

    @Test
    fun whenFetchAliasFromServiceAndAddressIsBlankThenStoreNull() = runTest {
        mockEmailDataStore.emailToken = "token"
        whenever(mockEmailService.newAlias(any())).thenReturn(EmailAlias(""))
        testee.getAlias()

        assertNull(mockEmailDataStore.nextAlias)
    }

    @Test
    fun whenGetAliasThenReturnNextAlias() = runTest {
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

        assertNull(mockEmailDataStore.nextAlias)
    }

    @Test
    fun whenIsSignedInAndTokenDoesNotExistThenReturnFalse() {
        mockEmailDataStore.emailUsername = "username"
        mockEmailDataStore.nextAlias = "alias"

        assertFalse(testee.isSignedIn())
    }

    @Test
    fun whenIsSignedInAndUsernameDoesNotExistThenReturnFalse() {
        mockEmailDataStore.emailToken = "token"
        mockEmailDataStore.nextAlias = "alias"

        assertFalse(testee.isSignedIn())
    }

    @Test
    fun whenIsSignedInAndTokenAndUsernameExistThenReturnTrue() {
        mockEmailDataStore.emailToken = "token"
        mockEmailDataStore.emailUsername = "username"

        assertTrue(testee.isSignedIn())
    }

    @Test
    fun whenStoreCredentialsThenGenerateNewAlias() = runTest {
        mockEmailDataStore.emailToken = "token"
        whenever(mockEmailService.newAlias(any())).thenReturn(EmailAlias(""))

        testee.storeCredentials("token", "username", "cohort")

        verify(mockEmailService).newAlias(any())
    }

    @Test
    fun whenStoreCredentialsThenCredentialsAreStoredInDataStore() {
        testee.storeCredentials("token", "username", "cohort")

        assertEquals("username", mockEmailDataStore.emailUsername)
        assertEquals("token", mockEmailDataStore.emailToken)
        assertEquals("cohort", mockEmailDataStore.cohort)
    }

    @Test
    fun whenStoreCredentialsIfCredentialsWereCorrectlyStoredThenIsSignedInChannelSendsTrue() = runTest {
        testee.storeCredentials("token", "username", "cohort")

        assertTrue(testee.signedInFlow().first())
    }

    @Test
    fun whenStoreCredentialsIfCredentialsAreBlankThenIsSignedInChannelSendsFalse() = runTest {
        testee.storeCredentials("", "", "cohort")

        assertFalse(testee.signedInFlow().first())
    }

    @Test
    fun whenSignedOutThenClearEmailDataAndAliasIsNull() {
        testee.signOut()

        assertNull(mockEmailDataStore.emailUsername)
        assertNull(mockEmailDataStore.emailToken)
        assertNull(mockEmailDataStore.nextAlias)

        assertNull(testee.getAlias())
    }

    @Test
    fun whenSignedOutThenIsSignedInChannelSendsFalse() = runTest {
        testee.signOut()

        assertFalse(testee.signedInFlow().first())
    }

    @Test
    fun whenGetEmailAddressThenDuckEmailDomainIsAppended() {
        mockEmailDataStore.emailUsername = "username"

        assertEquals("username$DUCK_EMAIL_DOMAIN", testee.getEmailAddress())
    }

    @Test
    fun whenWaitlistStateIfTimestampExistsCodeDoesNotExistAndSendNotificationIsTrueThenReturnJoinedQueueWithTrue() {
        mockEmailDataStore.waitlistTimestamp = 1234
        mockEmailDataStore.sendNotification = true

        assertEquals(JoinedQueue(true), testee.waitlistState())
    }

    @Test
    fun whenWaitlistStateIfTimestampExistsCodeDoesNotExistAndSendNotificationIsFalseThenReturnJoinedQueueWithFalse() {
        mockEmailDataStore.waitlistTimestamp = 1234
        mockEmailDataStore.sendNotification = false

        assertEquals(JoinedQueue(false), testee.waitlistState())
    }

    @Test
    fun whenWaitlistStateIfTimestampExistsAndCodeExistsThenReturnInBeta() {
        mockEmailDataStore.waitlistTimestamp = 1234
        mockEmailDataStore.inviteCode = "abcde"

        assertEquals(InBeta, testee.waitlistState())
    }

    @Test
    fun whenWaitlistStateIfTimestampAndCodeDoesNotExistThenReturnNotJoinedQueue() {
        mockEmailDataStore.waitlistTimestamp = -1
        mockEmailDataStore.waitlistToken = null

        assertEquals(NotJoinedQueue, testee.waitlistState())
    }

    @Test
    fun whenJoinWaitlistIfTimestampAndTokenDidNotExistThenStoreTimestampAndToken() {
        mockEmailDataStore.waitlistTimestamp = -1
        mockEmailDataStore.waitlistToken = null

        testee.joinWaitlist(1234, "abcde")

        assertEquals(1234, mockEmailDataStore.waitlistTimestamp)
        assertEquals("abcde", mockEmailDataStore.waitlistToken)
    }
    @Test
    fun whenJoinWaitlistIfTimestampAndTokenDidExistThenStoreTimestampAndTokenAreNotStored() {
        mockEmailDataStore.waitlistTimestamp = 1234
        mockEmailDataStore.waitlistToken = "abcde"

        testee.joinWaitlist(4321, "edcba")

        assertEquals(1234, mockEmailDataStore.waitlistTimestamp)
        assertEquals("abcde", mockEmailDataStore.waitlistToken)
    }

    @Test
    fun whenGetInviteCodeIfCodeExistsThenReturnCode() {
        mockEmailDataStore.inviteCode = "abcde"
        assertEquals("abcde", testee.getInviteCode())
    }

    @Test
    fun whenGetInviteCodeIfCodeDoesNotExistThenReturnEmpty() {
        mockEmailDataStore.inviteCode = null
        assertEquals("", testee.getInviteCode())
    }

    @Test
    fun whenDoesCodeAlreadyExistIfCodeExistsThenReturnTrue() {
        mockEmailDataStore.inviteCode = "inviteCode"

        assertTrue(testee.doesCodeAlreadyExist())
    }

    @Test
    fun whenDoesCodeAlreadyExistIfCodeIsNullThenReturnFalse() {
        mockEmailDataStore.inviteCode = null

        assertFalse(testee.doesCodeAlreadyExist())
    }

    @Test
    fun whenFetchInviteCodeIfCodeAlreadyExistsThenReturnCodeExisted() = runTest {
        mockEmailDataStore.inviteCode = "inviteCode"

        assertEquals(AppEmailManager.FetchCodeResult.CodeExisted, testee.fetchInviteCode())
    }

    @Test
    fun whenFetchInviteCodeIfTimestampIsSmallerThanQueueTimestampThenCallGetCode() = runTest {
        givenUserIsInWaitlist()
        whenever(mockEmailService.waitlistStatus()).thenReturn(WaitlistStatusResponse(12345))

        testee.fetchInviteCode()

        verify(mockEmailService).getCode("token")
    }

    @Test
    fun whenFetchInviteCodeIfTimestampIsEqualsThanQueueTimestampThenCallGetCode() = runTest {
        givenUserIsInWaitlist()
        whenever(mockEmailService.waitlistStatus()).thenReturn(WaitlistStatusResponse(1234))

        testee.fetchInviteCode()

        verify(mockEmailService).getCode("token")
    }

    @Test
    fun whenFetchInviteCodeIfUserIsTopOfQueueAndCodeAvailableThenReturnCode() = runTest {
        givenUserIsTopOfTheQueue()
        whenever(mockEmailService.getCode(any())).thenReturn(EmailInviteCodeResponse("code"))

        assertEquals(AppEmailManager.FetchCodeResult.Code, testee.fetchInviteCode())
    }

    @Test
    fun whenFetchInviteCodeIfUserIsTopOfQueueAndCodeNotAvailableThenReturnNoCode() = runTest {
        givenUserIsTopOfTheQueue()
        whenever(mockEmailService.getCode(any())).thenReturn(EmailInviteCodeResponse(""))

        assertEquals(AppEmailManager.FetchCodeResult.NoCode, testee.fetchInviteCode())
    }

    @Test
    fun whenFetchInviteCodeIfUserIsTopOfQueueAndCodeServiceNotAvailableThenReturnNoCode() = runTest {
        testee = AppEmailManager(TestEmailService(), mockEmailDataStore, coroutineRule.testDispatcherProvider, TestScope())
        givenUserIsTopOfTheQueue()

        assertEquals(AppEmailManager.FetchCodeResult.NoCode, testee.fetchInviteCode())
    }

    @Test
    fun whenFetchInviteCodeIfUserInTheQueueAndStatusServiceNotAvailableThenReturnNoCode() = runTest {
        testee = AppEmailManager(TestEmailService(), mockEmailDataStore, coroutineRule.testDispatcherProvider, TestScope())
        givenUserIsInWaitlist()

        assertEquals(AppEmailManager.FetchCodeResult.NoCode, testee.fetchInviteCode())
    }

    @Test
    fun whenNotifyOnJoinedWaitlistThenSendNotificationSetToTrue() {
        mockEmailDataStore.sendNotification = false
        testee.notifyOnJoinedWaitlist()
        assertTrue(mockEmailDataStore.sendNotification)
    }

    @Test
    fun whenGetCohortThenReturnCohort() {
        mockEmailDataStore.cohort = "cohort"

        assertEquals("cohort", testee.getCohort())
    }

    @Test
    fun whenGetCohortIfCohortIsNullThenReturnUnknown() {
        mockEmailDataStore.cohort = null

        assertEquals(UNKNOWN_COHORT, testee.getCohort())
    }

    @Test
    fun whenGetCohortIfCohortIsEmtpyThenReturnUnknown() {
        mockEmailDataStore.cohort = ""

        assertEquals(UNKNOWN_COHORT, testee.getCohort())
    }

    @Test
    fun whenIsEmailFeatureSupportedAndEncryptionCanBeUsedThenReturnTrue() {
        (mockEmailDataStore as FakeEmailDataStore).canUseEncryption = true

        assertTrue(testee.isEmailFeatureSupported())
    }

    @Test
    fun whenGetLastUsedDateIfNullThenReturnEmpty() {
        assertEquals("", testee.getLastUsedDate())
    }

    @Test
    fun whenGetLastUsedDateIfNotNullThenReturnValueFromStore() {
        mockEmailDataStore.lastUsedDate = "2021-01-01"
        assertEquals("2021-01-01", testee.getLastUsedDate())
    }

    @Test
    fun whenIsEmailFeatureSupportedAndEncryptionCannotBeUsedThenReturnFalse() {
        (mockEmailDataStore as FakeEmailDataStore).canUseEncryption = false

        assertFalse(testee.isEmailFeatureSupported())
    }

    private fun givenUserIsInWaitlist() {
        mockEmailDataStore.waitlistTimestamp = 1234
        mockEmailDataStore.waitlistToken = "token"
    }

    private fun givenUserIsTopOfTheQueue() = runTest {
        givenUserIsInWaitlist()
        whenever(mockEmailService.waitlistStatus()).thenReturn(WaitlistStatusResponse(1234))
    }

    private fun givenNextAliasExists() {
//        cachedAlias.set("alias")
        mockEmailDataStore.nextAlias = "alias"
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

class FakeEmailDataStore : EmailDataStore {
    override var emailToken: String? = null
    override var nextAlias: String? = null
    override var emailUsername: String? = null
    override var inviteCode: String? = null
    override var waitlistTimestamp: Int = 0
    override var waitlistToken: String? = null
    override var sendNotification: Boolean = false
    override var cohort: String? = null
    override var lastUsedDate: String? = null

    var canUseEncryption: Boolean = false
    override fun canUseEncryption(): Boolean = canUseEncryption

}
