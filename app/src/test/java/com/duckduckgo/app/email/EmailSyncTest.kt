package com.duckduckgo.app.email

import com.duckduckgo.app.email.sync.*
import com.duckduckgo.app.email.sync.EmailSync.DuckAddressSetting
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EmailSyncTest {

    private val emailManagerMock = mock<EmailManager>()
    private val testee = EmailSync(emailManagerMock)

    @Test
    fun whenUserSignedInThenReturnAccountInfo() {
        whenever(emailManagerMock.isSignedIn()).thenReturn(true)
        whenever(emailManagerMock.getEmailAddress()).thenReturn("email")
        whenever(emailManagerMock.getToken()).thenReturn("token")

        val value = testee.getValue()

        with(adapter.fromJson(value)!!) {
            assertEquals("email", this.main_duck_address)
            assertEquals("token", this.personal_access_token)
        }
    }

    @Test
    fun whenUserSignedOutThenReturnNull() {
        whenever(emailManagerMock.isSignedIn()).thenReturn(false)

        val value = testee.getValue()

        assertNull(value)
    }

    @Test
    fun whenSaveValueThenStoreCredentials() {
        testee.save("{\"main_duck_address\":\"email\",\"personal_access_token\":\"token\"}")

        verify(emailManagerMock).storeCredentials("email", "token", "")
    }

    @Test
    fun whenSaveNullThenLogoutUser() {
        testee.save(null)

        verify(emailManagerMock).signOut()
    }

    @Test
    fun whenMergeRemoteAddressWithSameLocalAddressThenDoNothing() {
        whenever(emailManagerMock.isSignedIn()).thenReturn(true)
        whenever(emailManagerMock.getEmailAddress()).thenReturn("email")
        whenever(emailManagerMock.getToken()).thenReturn("token")

        testee.mergeRemote("{\"main_duck_address\":\"email\",\"personal_access_token\":\"token\"}")

        verify(emailManagerMock, times(0)).storeCredentials(anyString(), anyString(), anyString())
    }

    @Test
    fun whenMergeRemoteAddressWithDifferentLocalAddressThenRemoteWins() {
        whenever(emailManagerMock.isSignedIn()).thenReturn(true)
        whenever(emailManagerMock.getEmailAddress()).thenReturn("email2")
        whenever(emailManagerMock.getToken()).thenReturn("token2")

        testee.mergeRemote("{\"main_duck_address\":\"email\",\"personal_access_token\":\"token\"}")

        verify(emailManagerMock).signOut()
        verify(emailManagerMock).storeCredentials("email", "token", "")
    }

    @Test
    fun whenMergeRemoteAddressWithNoLocalAccountThenStoreRemote() {
        whenever(emailManagerMock.isSignedIn()).thenReturn(false)

        testee.mergeRemote("{\"main_duck_address\":\"email\",\"personal_access_token\":\"token\"}")

        verify(emailManagerMock).storeCredentials("email", "token", "")
    }

    @Test
    fun whenMergeNullAddresThenDoNothing() {
        whenever(emailManagerMock.isSignedIn()).thenReturn(true)
        whenever(emailManagerMock.getEmailAddress()).thenReturn("email")
        whenever(emailManagerMock.getToken()).thenReturn("token")

        testee.mergeRemote(null)

        verify(emailManagerMock, times(0))
    }

    companion object {
        private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter: JsonAdapter<DuckAddressSetting> = moshi.adapter(DuckAddressSetting::class.java).lenient()
    }
}
