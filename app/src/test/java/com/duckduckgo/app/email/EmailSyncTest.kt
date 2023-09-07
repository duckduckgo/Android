package com.duckduckgo.app.email

import com.duckduckgo.app.email.db.*
import com.duckduckgo.app.email.sync.*
import com.duckduckgo.settings.api.*
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.mockito.kotlin.*

class EmailSyncTest {

    private val emailManagerMock = mock<EmailDataStore>()
    private val syncSettingsListenerMock = mock<SyncSettingsListener>()

    private val testee = EmailSync(emailManagerMock, syncSettingsListenerMock)

    @Test
    fun whenUserSignedInThenReturnAccountInfo() {
        whenever(emailManagerMock.emailUsername).thenReturn("username")
        whenever(emailManagerMock.emailToken).thenReturn("token")

        val value = testee.getValue()

        with(adapter.fromJson(value)!!) {
            assertEquals("username", this.username)
            assertEquals("token", this.personal_access_token)
        }
    }

    @Test
    fun whenUserSignedOutThenReturnNull() {
        whenever(emailManagerMock.emailUsername).thenReturn(null)
        whenever(emailManagerMock.emailToken).thenReturn(null)

        val value = testee.getValue()

        assertNull(value)
    }

    @Test
    fun whenSaveValueThenStoreCredentials() {
        testee.save("{\"username\":\"email\",\"personal_access_token\":\"token\"}")

        verify(emailManagerMock).emailUsername = "email"
        verify(emailManagerMock).emailToken = "token"
    }

    @Test
    fun whenSaveNullThenLogoutUser() {
        testee.save(null)

        verify(emailManagerMock).emailUsername = ""
        verify(emailManagerMock).emailToken = ""
    }

    @Test
    fun whenMergeRemoteAddressWithSameLocalAddressThenDoNothing() {
        whenever(emailManagerMock.emailUsername).thenReturn("username")
        whenever(emailManagerMock.emailToken).thenReturn("token")

        testee.mergeRemote("{\"username\":\"email\",\"personal_access_token\":\"token\"}")

        verify(emailManagerMock).emailUsername = "email"
        verify(emailManagerMock).emailToken = "token"
    }

    @Test
    fun whenMergeRemoteAddressWithDifferentLocalAddressThenRemoteWins() {
        whenever(emailManagerMock.emailUsername).thenReturn("username2")
        whenever(emailManagerMock.emailToken).thenReturn("token2")

        testee.mergeRemote("{\"username\":\"email\",\"personal_access_token\":\"token\"}")

        verify(emailManagerMock).emailUsername = "email"
        verify(emailManagerMock).emailToken = "token"
    }

    @Test
    fun whenMergeRemoteAddressWithNoLocalAccountThenStoreRemote() {
        whenever(emailManagerMock.emailUsername).thenReturn(null)
        whenever(emailManagerMock.emailToken).thenReturn(null)

        testee.mergeRemote("{\"username\":\"email\",\"personal_access_token\":\"token\"}")

        verify(emailManagerMock).emailUsername = "email"
        verify(emailManagerMock).emailToken = "token"
    }

    @Test
    fun whenMergeNullAddresThenDoNothing() {
        whenever(emailManagerMock.emailUsername).thenReturn("username")
        whenever(emailManagerMock.emailToken).thenReturn("token")

        testee.mergeRemote(null)

        verify(emailManagerMock, times(0)).emailToken
        verify(emailManagerMock, times(0)).emailUsername
    }

    companion object {
        private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter: JsonAdapter<DuckAddressSetting> = moshi.adapter(DuckAddressSetting::class.java).lenient()
    }
}
