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
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.*
import com.duckduckgo.app.email.AppEmailManager.Companion.DUCK_EMAIL_DOMAIN
import com.duckduckgo.app.email.AppEmailManager.Companion.UNKNOWN_COHORT
import com.duckduckgo.app.email.api.EmailAlias
import com.duckduckgo.app.email.api.EmailService
import com.duckduckgo.app.email.db.EmailDataStore
import com.duckduckgo.app.email.sync.*
import com.duckduckgo.app.pixels.AppPixelName.EMAIL_DISABLED
import com.duckduckgo.app.pixels.AppPixelName.EMAIL_ENABLED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.settings.api.SyncSettingsListener
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@FlowPreview
@RunWith(AndroidJUnit4::class)
class AppEmailManagerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val mockEmailService: EmailService = mock()
    private val mockEmailDataStore: EmailDataStore = FakeEmailDataStore()
    private val mockSyncSettingsListener = mock<SyncSettingsListener>()
    private val emailSyncableSetting = EmailSync(mockEmailDataStore, mockSyncSettingsListener, mock())
    private val mockPixel: Pixel = mock()
    lateinit var testee: AppEmailManager

    @Before
    fun setup() {
        testee = AppEmailManager(
            mockEmailService,
            mockEmailDataStore,
            emailSyncableSetting,
            coroutineRule.testDispatcherProvider,
            TestScope(),
            mockPixel,
        )
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
    fun whenStoreCredentialsThenNotifySyncableSetting() = runTest {
        mockEmailDataStore.emailToken = "token"
        whenever(mockEmailService.newAlias(any())).thenReturn(EmailAlias(""))

        testee.storeCredentials("token", "username", "cohort")

        verify(mockSyncSettingsListener).onSettingChanged(emailSyncableSetting.key)
    }

    @Test
    fun whenStoreCredentialsThenSendPixel() = runTest {
        mockEmailDataStore.emailToken = "token"
        whenever(mockEmailService.newAlias(any())).thenReturn(EmailAlias(""))

        testee.storeCredentials("token", "username", "cohort")

        verify(mockPixel).fire(EMAIL_ENABLED)
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
    fun whenSignedOutThenNotifySyncableSetting() {
        testee.signOut()

        verify(mockSyncSettingsListener).onSettingChanged(emailSyncableSetting.key)
    }

    @Test
    fun whenSignedOutThenSendPixel() {
        testee.signOut()

        verify(mockPixel).fire(EMAIL_DISABLED)
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

    @Test
    fun whenGetUserDataThenDataReceivedCorrectly() {
        val expected = JSONObject().apply {
            put(AppEmailManager.TOKEN, "token")
            put(AppEmailManager.USERNAME, "user")
            put(AppEmailManager.NEXT_ALIAS, "nextAlias")
        }.toString()

        mockEmailDataStore.emailToken = "token"
        mockEmailDataStore.emailUsername = "user"
        mockEmailDataStore.nextAlias = "nextAlias@duck.com"

        assertEquals(expected, testee.getUserData())
    }

    @Test
    fun whenSyncableSettingNotifiesChangeThenRefreshEmailState() = runTest {
        testee.signedInFlow().test {
            assertFalse(awaitItem())
            emailSyncableSetting.save("{\"username\":\"email\",\"personal_access_token\":\"token\"}")
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun givenNextAliasExists() {
        mockEmailDataStore.nextAlias = "alias"
    }

    class TestEmailService : EmailService {
        override suspend fun newAlias(authorization: String): EmailAlias = EmailAlias("alias")
    }
}

class FakeEmailDataStore : EmailDataStore {
    override var emailToken: String? = null
    override var nextAlias: String? = null
    override var emailUsername: String? = null
    override var cohort: String? = null
    override var lastUsedDate: String? = null

    var canUseEncryption: Boolean = false
    override fun canUseEncryption(): Boolean = canUseEncryption
}
