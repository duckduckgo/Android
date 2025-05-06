package com.duckduckgo.app.email

import com.duckduckgo.app.email.db.*
import com.duckduckgo.app.email.sync.*
import com.duckduckgo.app.pixels.*
import com.duckduckgo.app.statistics.pixels.*
import com.duckduckgo.sync.settings.api.SyncSettingsListener
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.mockito.kotlin.*

class EmailSyncTest {

    private val emailDataStoreMock = mock<EmailDataStore>()
    private val syncSettingsListenerMock = mock<SyncSettingsListener>()
    private val pixelMock = mock<Pixel>()

    private val testee = EmailSync(emailDataStoreMock, syncSettingsListenerMock, pixelMock)

    @Test
    fun whenUserSignedInThenReturnAccountInfo() = runTest {
        whenever(emailDataStoreMock.getEmailUsername()).thenReturn("username")
        whenever(emailDataStoreMock.getEmailToken()).thenReturn("token")

        val value = testee.getValue()

        with(adapter.fromJson(value)!!) {
            assertEquals("username", this.username)
            assertEquals("token", this.personal_access_token)
        }
    }

    @Test
    fun whenUserSignedOutThenReturnNull() = runTest {
        whenever(emailDataStoreMock.getEmailUsername()).thenReturn(null)
        whenever(emailDataStoreMock.getEmailToken()).thenReturn(null)

        val value = testee.getValue()

        assertNull(value)
    }

    @Test
    fun whenSaveValueThenStoreCredentials() = runTest {
        testee.save("{\"username\":\"email\",\"personal_access_token\":\"token\"}")

        verify(emailDataStoreMock).setEmailUsername("email")
        verify(emailDataStoreMock).setEmailToken("token")
    }

    @Test
    fun whenSaveNullThenLogoutUser() = runTest {
        testee.save(null)

        verify(emailDataStoreMock).setEmailUsername("")
        verify(emailDataStoreMock).setEmailToken("")
    }

    @Test
    fun whenDeduplicateRemoteAddressWithSameLocalAddressThenDoNothing() = runTest {
        whenever(emailDataStoreMock.getEmailUsername()).thenReturn("username")
        whenever(emailDataStoreMock.getEmailToken()).thenReturn("token")

        testee.deduplicate("{\"username\":\"email\",\"personal_access_token\":\"token\"}")

        verify(emailDataStoreMock).setEmailUsername("email")
        verify(emailDataStoreMock).setEmailToken("token")
    }

    @Test
    fun whenDeduplicateRemoteAddressWithDifferentLocalAddressThenRemoteWins() = runTest {
        whenever(emailDataStoreMock.getEmailUsername()).thenReturn("username2")
        whenever(emailDataStoreMock.getEmailToken()).thenReturn("token2")

        testee.deduplicate("{\"username\":\"email\",\"personal_access_token\":\"token\"}")

        verify(emailDataStoreMock).setEmailUsername("email")
        verify(emailDataStoreMock).setEmailToken("token")
    }

    @Test
    fun whenDeduplicateRemoteAddressWithDifferentLocalAddressThenPixelEvent() = runTest {
        whenever(emailDataStoreMock.getEmailUsername()).thenReturn("username2")
        whenever(emailDataStoreMock.getEmailToken()).thenReturn("token2")

        testee.deduplicate("{\"username\":\"email\",\"personal_access_token\":\"token\"}")

        verify(pixelMock).fire(AppPixelName.DUCK_EMAIL_OVERRIDE_PIXEL)
    }

    @Test
    fun whenDeduplicateRemoteAddressWithNoLocalAccountThenStoreRemote() = runTest {
        whenever(emailDataStoreMock.getEmailUsername()).thenReturn(null)
        whenever(emailDataStoreMock.getEmailToken()).thenReturn(null)

        testee.deduplicate("{\"username\":\"email\",\"personal_access_token\":\"token\"}")

        verify(emailDataStoreMock).setEmailUsername("email")
        verify(emailDataStoreMock).setEmailToken("token")
    }

    @Test
    fun whenDeduplicateNullAddresThenDoNothing() = runTest {
        whenever(emailDataStoreMock.getEmailUsername()).thenReturn("username")
        whenever(emailDataStoreMock.getEmailToken()).thenReturn("token")

        testee.deduplicate(null)

        verify(emailDataStoreMock, times(0)).getEmailToken()
        verify(emailDataStoreMock, times(0)).getEmailUsername()
    }

    companion object {
        private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter: JsonAdapter<DuckAddressSetting> = moshi.adapter(DuckAddressSetting::class.java).lenient()
    }
}
