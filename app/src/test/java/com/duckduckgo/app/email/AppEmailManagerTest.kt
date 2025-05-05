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
        mockEmailDataStore.setEmailToken("token")
        whenever(mockEmailService.newAlias(any())).thenReturn(EmailAlias("test"))
        testee.getAlias()

        assertEquals("test$DUCK_EMAIL_DOMAIN", mockEmailDataStore.getNextAlias())
    }

    @Test
    fun whenFetchAliasFromServiceAndTokenDoesNotExistThenDoNothing() = runTest {
        mockEmailDataStore.setEmailToken(null)
        testee.getAlias()

        verify(mockEmailService, never()).newAlias(any())
    }

    @Test
    fun whenFetchAliasFromServiceAndAddressIsBlankThenStoreNull() = runTest {
        mockEmailDataStore.setEmailToken("token")
        whenever(mockEmailService.newAlias(any())).thenReturn(EmailAlias(""))
        testee.getAlias()

        assertNull(mockEmailDataStore.getNextAlias())
    }

    @Test
    fun whenGetAliasThenReturnNextAlias() = runTest {
        givenNextAliasExists()

        assertEquals("alias", testee.getAlias())
    }

    @Test
    fun whenGetAliasIfNextAliasDoesNotExistThenReturnNull() = runTest {
        assertNull(testee.getAlias())
    }

    @Test
    fun whenGetAliasThenClearNextAlias() = runTest {
        testee.getAlias()

        assertNull(mockEmailDataStore.getNextAlias())
    }

    @Test
    fun whenIsSignedInAndTokenDoesNotExistThenReturnFalse() = runTest {
        mockEmailDataStore.setEmailUsername("username")
        mockEmailDataStore.setNextAlias("alias")

        assertFalse(testee.isSignedIn())
    }

    @Test
    fun whenIsSignedInAndUsernameDoesNotExistThenReturnFalse() = runTest {
        mockEmailDataStore.setEmailToken("token")
        mockEmailDataStore.setNextAlias("alias")

        assertFalse(testee.isSignedIn())
    }

    @Test
    fun whenIsSignedInAndTokenAndUsernameExistThenReturnTrue() = runTest {
        mockEmailDataStore.setEmailToken("token")
        mockEmailDataStore.setEmailUsername("username")

        assertTrue(testee.isSignedIn())
    }

    @Test
    fun whenStoreCredentialsThenGenerateNewAlias() = runTest {
        mockEmailDataStore.setEmailToken("token")
        whenever(mockEmailService.newAlias(any())).thenReturn(EmailAlias(""))

        testee.storeCredentials("token", "username", "cohort")

        verify(mockEmailService).newAlias(any())
    }

    @Test
    fun whenStoreCredentialsThenNotifySyncableSetting() = runTest {
        mockEmailDataStore.setEmailToken("token")
        whenever(mockEmailService.newAlias(any())).thenReturn(EmailAlias(""))

        testee.storeCredentials("token", "username", "cohort")

        verify(mockSyncSettingsListener).onSettingChanged(emailSyncableSetting.key)
    }

    @Test
    fun whenStoreCredentialsThenSendPixel() = runTest {
        mockEmailDataStore.setEmailToken("token")
        whenever(mockEmailService.newAlias(any())).thenReturn(EmailAlias(""))

        testee.storeCredentials("token", "username", "cohort")

        verify(mockPixel).fire(EMAIL_ENABLED)
    }

    @Test
    fun whenStoreCredentialsThenCredentialsAreStoredInDataStore() = runTest {
        whenever(mockEmailService.newAlias(any())).thenReturn(EmailAlias(""))

        testee.storeCredentials("token", "username", "cohort")

        assertEquals("username", mockEmailDataStore.getEmailUsername())
        assertEquals("token", mockEmailDataStore.getEmailToken())
        assertEquals("cohort", mockEmailDataStore.getCohort())
    }

    @Test
    fun whenStoreCredentialsIfCredentialsWereCorrectlyStoredThenIsSignedInChannelSendsTrue() = runTest {
        whenever(mockEmailService.newAlias(any())).thenReturn(EmailAlias(""))

        testee.storeCredentials("token", "username", "cohort")

        assertTrue(testee.signedInFlow().first())
    }

    @Test
    fun whenStoreCredentialsIfCredentialsAreBlankThenIsSignedInChannelSendsFalse() = runTest {
        whenever(mockEmailService.newAlias(any())).thenReturn(EmailAlias(""))

        testee.storeCredentials("", "", "cohort")

        assertFalse(testee.signedInFlow().first())
    }

    @Test
    fun whenSignedOutThenClearEmailDataAndAliasIsNull() = runTest {
        testee.signOut()

        assertNull(mockEmailDataStore.getEmailUsername())
        assertNull(mockEmailDataStore.getEmailToken())
        assertNull(mockEmailDataStore.getNextAlias())

        assertNull(testee.getAlias())
    }

    @Test
    fun whenSignedOutThenNotifySyncableSetting() = runTest {
        testee.signOut()

        verify(mockSyncSettingsListener).onSettingChanged(emailSyncableSetting.key)
    }

    @Test
    fun whenSignedOutThenSendPixel() = runTest {
        testee.signOut()

        verify(mockPixel).fire(EMAIL_DISABLED)
    }

    @Test
    fun whenSignedOutThenIsSignedInChannelSendsFalse() = runTest {
        testee.signOut()

        assertFalse(testee.signedInFlow().first())
    }

    @Test
    fun whenGetEmailAddressThenDuckEmailDomainIsAppended() = runTest {
        mockEmailDataStore.setEmailUsername("username")

        assertEquals("username$DUCK_EMAIL_DOMAIN", testee.getEmailAddress())
    }

    @Test
    fun whenGetCohortThenReturnCohort() = runTest {
        mockEmailDataStore.setCohort("cohort")

        assertEquals("cohort", testee.getCohort())
    }

    @Test
    fun whenGetCohortIfCohortIsNullThenReturnUnknown() = runTest {
        mockEmailDataStore.setCohort(null)

        assertEquals(UNKNOWN_COHORT, testee.getCohort())
    }

    @Test
    fun whenGetCohortIfCohortIsEmtpyThenReturnUnknown() = runTest {
        mockEmailDataStore.setCohort("")

        assertEquals(UNKNOWN_COHORT, testee.getCohort())
    }

    @Test
    fun whenIsEmailFeatureSupportedAndEncryptionCanBeUsedThenReturnTrue() = runTest {
        (mockEmailDataStore as FakeEmailDataStore).canUseEncryption = true

        assertTrue(testee.isEmailFeatureSupported())
    }

    @Test
    fun whenGetLastUsedDateIfNullThenReturnEmpty() = runTest {
        assertEquals("", testee.getLastUsedDate())
    }

    @Test
    fun whenGetLastUsedDateIfNotNullThenReturnValueFromStore() = runTest {
        mockEmailDataStore.setLastUsedDate("2021-01-01")
        assertEquals("2021-01-01", testee.getLastUsedDate())
    }

    @Test
    fun whenIsEmailFeatureSupportedAndEncryptionCannotBeUsedThenReturnFalse() = runTest {
        (mockEmailDataStore as FakeEmailDataStore).canUseEncryption = false

        assertFalse(testee.isEmailFeatureSupported())
    }

    @Test
    fun whenGetUserDataThenDataReceivedCorrectly() = runTest {
        val expected = JSONObject().apply {
            put(AppEmailManager.TOKEN, "token")
            put(AppEmailManager.USERNAME, "user")
            put(AppEmailManager.NEXT_ALIAS, "nextAlias")
        }.toString()

        mockEmailDataStore.setEmailToken("token")
        mockEmailDataStore.setEmailUsername("user")
        mockEmailDataStore.setNextAlias("nextAlias@duck.com")

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

    private fun givenNextAliasExists() = runTest {
        mockEmailDataStore.setNextAlias("alias")
    }

    class TestEmailService : EmailService {
        override suspend fun newAlias(authorization: String): EmailAlias = EmailAlias("alias")
    }
}

class FakeEmailDataStore : EmailDataStore {

    private var _emailToken: String? = null
    override suspend fun setEmailToken(value: String?) {
        _emailToken = value
    }
    override suspend fun getEmailToken(): String? = _emailToken

    private var _nextAlias: String? = null
    override suspend fun getNextAlias(): String? = _nextAlias
    override suspend fun setNextAlias(value: String?) {
        _nextAlias = value
    }

    private var _emailUsername: String? = null
    override suspend fun getEmailUsername(): String? = _emailUsername
    override suspend fun setEmailUsername(value: String?) {
        _emailUsername = value
    }

    private var _cohort: String? = null
    override suspend fun getCohort(): String? = _cohort
    override suspend fun setCohort(value: String?) {
        _cohort = value
    }

    private var _lastUsedDate: String? = null
    override suspend fun getLastUsedDate(): String? = _lastUsedDate
    override suspend fun setLastUsedDate(value: String?) {
        _lastUsedDate = value
    }

    var canUseEncryption: Boolean = false
    override suspend fun canUseEncryption(): Boolean = canUseEncryption
}
