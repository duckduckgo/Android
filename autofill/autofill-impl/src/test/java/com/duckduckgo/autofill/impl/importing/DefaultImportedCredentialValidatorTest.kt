package com.duckduckgo.autofill.impl.importing

import org.junit.Assert.*
import org.junit.Test

class DefaultImportedCredentialValidatorTest {
    private val testee = DefaultImportedCredentialValidator()

    @Test
    fun whenAllFieldsPopulatedThenIsValid() {
        assertTrue(testee.isValid(fullyPopulatedCredentials()))
    }

    @Test
    fun whenNoFieldsPopulatedThenIsInvalid() {
        assertFalse(testee.isValid(emptyCredentials()))
    }

    @Test
    fun whenUsernameMissingThenIsValid() {
        val missingUsername = fullyPopulatedCredentials().copy(username = null)
        assertTrue(testee.isValid(missingUsername))
    }

    @Test
    fun whenPasswordMissingThenIsValid() {
        val missingPassword = fullyPopulatedCredentials().copy(password = null)
        assertTrue(testee.isValid(missingPassword))
    }

    @Test
    fun whenDomainMissingThenIsValid() {
        val missingDomain = fullyPopulatedCredentials().copy(url = null)
        assertTrue(testee.isValid(missingDomain))
    }

    @Test
    fun whenTitleIsMissingThenIsValid() {
        val missingTitle = fullyPopulatedCredentials().copy(title = null)
        assertTrue(testee.isValid(missingTitle))
    }

    @Test
    fun whenNotesIsMissingThenIsValid() {
        assertTrue(testee.isValid(fullyPopulatedCredentials().copy(notes = null)))
    }

    @Test
    fun whenUsernameOnlyFieldPopulatedThenIsValid() {
        assertTrue(testee.isValid(emptyCredentials().copy(username = "user")))
    }

    @Test
    fun whenPasswordOnlyFieldPopulatedThenIsValid() {
        assertTrue(testee.isValid(emptyCredentials().copy(password = "password")))
    }

    @Test
    fun whenDomainOnlyFieldPopulatedThenIsValid() {
        assertTrue(testee.isValid(emptyCredentials().copy(url = "example.com")))
    }

    @Test
    fun whenTitleIsOnlyFieldPopulatedThenIsValid() {
        assertTrue(testee.isValid(emptyCredentials().copy(title = "title")))
    }

    @Test
    fun whenNotesIsOnlyFieldPopulatedThenIsValid() {
        assertTrue(testee.isValid(emptyCredentials().copy(notes = "notes")))
    }

    @Test
    fun whenDomainIsAppPasswordThenIsNotValid() {
        val appPassword = fullyPopulatedCredentials().copy(url = "android://Jz-U_hg==@com.netflix.mediaclient/")
        assertFalse(testee.isValid(appPassword))
    }

    private fun fullyPopulatedCredentials(): GoogleCsvLoginCredential {
        return GoogleCsvLoginCredential(
            username = "username",
            password = "password",
            url = "example.com",
            title = "example title",
            notes = "notes",
        )
    }

    private fun emptyCredentials(): GoogleCsvLoginCredential {
        return GoogleCsvLoginCredential(
            username = null,
            password = null,
            url = null,
            title = null,
            notes = null,
        )
    }
}
